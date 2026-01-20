import os
import sys
import json
import time
from google import genai
from google.genai import types

import sys
# Force the standard output to use UTF-8 encoding
if sys.stdout.encoding != 'utf-8':
    sys.stdout.reconfigure(encoding='utf-8')

# 1. Setup absolute paths so the server always finds your files
SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))

# Automatically find all CSV files in the directory
file_paths = [
    os.path.join(SCRIPT_DIR, f)
    for f in os.listdir(SCRIPT_DIR)
    if f.endswith('.csv')
]

client = genai.Client(api_key='AIzaSyDesDATQfffLFreqt8m3jX5CxVii5J-EYg')

# Cache file to store uploaded file URIs
CACHE_FILE = os.path.join(SCRIPT_DIR, '.uploaded_files_cache.json')

SYSTEM_INSTRUCTION = """You are an expert analyst for a Minecraft plugin.
1. 'villager_menu.csv': the enchantments, linked to the villager name so that players can find them.
2. 'scan_results.csv': Inventory for World (Master Inventory).
3. '<player_name>.csv': The inventory for the player by that name.

FORMATTING RULES:
- Use Minecraft color codes (using the § symbol) for all responses.
- Use §6 (Gold) for headings or category names.
- Use §b (Aqua) for item names or quantities.
- Use §a (Lime Green) for villager names or positive results.
- Use §c (Red) for missing items or warnings.
- Always prefix your entire response with §f (White) to ensure clean text.
- Do not refer to these by filenames. Refer to them as master inventory, <player_name>'s inventory, and villager menu.
- Keep responses brief and to the point.
- Add indents to create hierarchical answers to prompts
- Do not use asterisks (*) for bolding or lists; use the color codes instead.
"""

def get_uploaded_files():
    """Get uploaded files, using cache if valid (files stay on Google for 48hrs)"""
    cache = {}
    if os.path.exists(CACHE_FILE):
        try:
            with open(CACHE_FILE, 'r') as f:
                cache = json.load(f)
        except:
            cache = {}

    current_time = time.time()
    uploaded_files = []
    needs_update = False

    for file_path in file_paths:
        file_name = os.path.basename(file_path)
        file_mtime = os.path.getmtime(file_path)

        # Check if file is cached and still valid
        if file_name in cache:
            cached_entry = cache[file_name]
            upload_time = cached_entry.get('upload_time', 0)
            cached_mtime = cached_entry.get('mtime', 0)

            # Valid if uploaded within 48 hours and file hasn't been modified
            if (current_time - upload_time < 48 * 3600) and (file_mtime == cached_mtime):
                # Reuse the cached file URI
                uploaded_files.append(types.Part.from_uri(
                    file_uri=cached_entry['file_uri'],
                    mime_type=cached_entry['mime_type']
                ))
                continue

        # Upload new file
        uploaded_file = client.files.upload(file=file_path)
        uploaded_files.append(uploaded_file)

        # Update cache
        cache[file_name] = {
            'file_uri': uploaded_file.uri,
            'mime_type': uploaded_file.mime_type,
            'upload_time': current_time,
            'mtime': file_mtime
        }
        needs_update = True

    if needs_update:
        with open(CACHE_FILE, 'w') as f:
            json.dump(cache, f)

    return uploaded_files

def get_clean_answer():
    model_id = 'gemini-3-flash-preview'
    uploaded_files = get_uploaded_files()
    user_query = " ".join(sys.argv[1:]) if len(sys.argv) > 1 else "Analyze inventories."

    response = client.models.generate_content_stream(
        model=model_id,
        contents=uploaded_files + [user_query],
        config=types.GenerateContentConfig(
            # REMOVE code_execution for instant results with small CSVs
            tools=[
                {'code_execution': {}},
                {'google_search': {}}
            ],
            system_instruction="""You are an expert minecraft anaylst. You have access to the internet as well but also to the data specific to this minecraft world. 
1. 'villager_menu.csv': the enchantments, linked to the villager name so that players can find them.
2. 'scan_results.csv': Inventory for World (Master Inventory).
3. '<player_name>.csv': The inventory for the player by that name.

FORMATTING RULES:
- Use Minecraft color codes (using the § symbol) for all responses.
- Use §6 (Gold) for headings or category names.
- Use §b (Aqua) for item names or quantities.
- Use §a (Lime Green) for villager names or positive results.
- Use §c (Red) for missing items or warnings.
- Always prefix your entire response with §f (White) to ensure clean text.
- Do not refer to these by filenames. Refer to them as master inventory, <player_name>'s inventory, and villager menu.
- Keep responses brief and to the point.
- Add indents to create hierarchical answers to prompts
- Do not use asterisks (*) for bolding or lists; use the color codes instead.
"""
        )
    )



    # Character-by-character streaming (No more waiting for newlines)
    for chunk in response:
        if chunk.candidates:
            for part in chunk.candidates[0].content.parts:
                if part.text:
                    print(part.text, end="", flush=True)
    print() # Final newline

if __name__ == "__main__":
    get_clean_answer()