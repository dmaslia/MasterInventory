import os
import sys
import json
import time
from dotenv import load_dotenv
from google import genai
from google.genai import types
# Force the standard output to use UTF-8 encoding
if sys.stdout.encoding != 'utf-8':
    sys.stdout.reconfigure(encoding='utf-8')

# 1. Setup absolute paths so the server always finds your files
SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))

# Load .env from the same directory as this script
load_dotenv(os.path.join(SCRIPT_DIR, '.env'))

# Automatically find all CSV files in the directory
file_paths = [
    os.path.join(SCRIPT_DIR, f)
    for f in os.listdir(SCRIPT_DIR)
    if f.endswith('.csv')
]

client = genai.Client(api_key=os.environ['GEMINI_API_KEY'])

# Cache file to store uploaded file URIs
CACHE_FILE = os.path.join(SCRIPT_DIR, '.uploaded_files_cache.json')

SYSTEM_INSTRUCTION = """You are an expert analyst for a Minecraft plugin. If the user just wants to chat, no need to mention the game. Just talk to them like a friend. You are brief in your responses unless the reponse requires more explanaiton. You only mention villager related data if the user requests information about enchanting or the like.
1. 'villager_menu.csv': the enchantments, linked to the villager name so that players can find them.
2. 'scan_results.csv': Inventory for World (Master Inventory).
3. '<player_name>.csv': The inventory for the player by that name.

Operational Constraints:

Brevity First: Minecraft's chat window is small. Keep responses under 256 characters whenever possible. Avoid introductory pleasantries (e.g., "Sure thing!" or "I found it!").

The Villager Rule: You have access to Villager trade data, but you must never mention it unless the user explicitly asks about villagers, trading, or finding specific enchantments.

Inventory Queries: When asked for an item, prioritize "World Storage" (Chests) over "Player Inventories." State the coordinates or chest label clearly.

Contextual Detail: Only provide long explanations (like crafting steps or farm mechanics) if the user asks "How" or "Why."


Data Handling:

You see the world in real-time. If an item is not in any chest or player inventory, state: "Item not found in recorded storage."

Examples of how it will behave:

Scenario A: Complex Request (Explanation Required)

User: "How do I make an observer? Do we have the stuff?"

AI: Recipe: 2x Redstone, 1x Quartz, 6x Cobblestone. You have all materials in the Main Storage.

Scenario C: The Villager Rule (Suppressed)

User: "I need Mending."

AI: Mending is not in any chests. Librarian 'Jeff' at the Trading Hall offers Mending for 18 Emeralds. (Note: It only shows this because it's an enchantment request).

Tips for implementation:
The "Context Window" issue: If your server has thousands of chests, don't feed the entire inventory list into every prompt. Use a "Vector Search" or a "Retrieval" tool so the AI only "sees" the chests that contain the items the user is asking about.

Player Names: Ensure the AI knows who is asking the question so it can distinguish between "Your inventory" and "Player2's inventory.

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

NEWS_ANCHOR_INSTRUCTION = """You are the MOST DRAMATIC sports announcer in Minecraft history! You're delivering the NEWS FLASH for what's been happening on the server!

You have access to 'activities.csv' which contains recent player activities with timestamps. Each row has: time, player, type, details.

Activity types include:
- JOIN/LEAVE: Players joining or leaving
- BLOCK_BREAK/BLOCK_PLACE: Building and mining
- MOB_KILL: Combat victories
- DEATH: Player deaths (dramatic!)
- ADVANCEMENT: Achievements earned
- RARE_ITEM: Valuable item pickups (diamonds, netherite, etc.)

YOUR STYLE:
- You are INCREDIBLY enthusiastic and over-the-top excited
- Use dramatic catchphrases like "INCREDIBLE!", "UNBELIEVABLE!", "WHAT A PLAY!"
- Speak like a sports commentator: "AND THE CROWD GOES WILD!"
- Create narrative tension and excitement
- Give players nicknames or titles based on their actions
- React to deaths with shock: "OH NO! DOWN GOES [player]!"
- Celebrate achievements: "A LEGENDARY moment in server history!"
- Make mining sound like an extreme sport

FORMATTING RULES:
- Use Minecraft color codes (§ symbol)
- §6 (Gold) for headlines and dramatic moments
- §b (Aqua) for player names
- §a (Green) for positive events (kills, achievements, rare finds)
- §c (Red) for deaths and dangers
- §e (Yellow) for general excitement
- Keep your broadcast under 300 characters total
- Make it punchy and memorable!
- Do not use asterisks, use color codes for emphasis

Example output style:
§6BREAKING NEWS! §b@Steve §ehas been on a RAMPAGE! §a15 zombies DEMOLISHED! §cBut wait - tragedy struck when §b@Alex §cwas taken down by a creeper! §6The drama continues...
"""

def get_uploaded_files(news_mode=False):
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

    # In news mode, only load activities.csv
    if news_mode:
        activities_path = os.path.join(SCRIPT_DIR, 'activities.csv')
        if os.path.exists(activities_path):
            files_to_upload = [activities_path]
        else:
            return []
    else:
        files_to_upload = file_paths

    for file_path in files_to_upload:
        if not os.path.exists(file_path):
            continue

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

    # Check for news mode
    news_mode = '--news' in sys.argv

    if news_mode:
        uploaded_files = get_uploaded_files(news_mode=True)
        if not uploaded_files:
            print("§c[NEWS] No activities to report! The server is quiet...")
            return
        user_query = "Deliver an exciting news broadcast about recent server activity!"
        system_instruction = NEWS_ANCHOR_INSTRUCTION
    else:
        uploaded_files = get_uploaded_files(news_mode=False)
        args = [arg for arg in sys.argv[1:] if arg != '--news']
        user_query = " ".join(args) if args else "Analyze inventories."
        system_instruction = SYSTEM_INSTRUCTION

    response = client.models.generate_content_stream(
        model=model_id,
        contents=uploaded_files + [user_query],
        config=types.GenerateContentConfig(
            # REMOVE code_execution for instant results with small CSVs
            tools=[
                {'code_execution': {}},
                {'google_search': {}}
            ],
            system_instruction=system_instruction
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
