import streamlit as st
import pandas as pd
import os
import time
import glob

current_dir = os.path.dirname(os.path.abspath(__file__))
INVENTORY_DIR = os.path.join(current_dir, "plugins", "MasterInventory")

st.set_page_config(page_title="Inventory Scanner", layout="wide")

# Custom CSS for better styling
st.markdown("""
<style>
    /* Hide hamburger menu and header completely */
    #MainMenu {display: none !important;}
    header {display: none !important;}
    .stAppHeader {display: none !important;}
    [data-testid="stHeader"] {display: none !important;}
    .stAppToolbar {display: none !important;}
    [data-testid="stToolbar"] {display: none !important;}

    /* Remove Streamlit's default top padding */
    .st-emotion-cache-zy6yx3 {
        padding-top: 0rem !important;
    }

    /* Main container */
    .main .block-container {
        padding: 5rem 3rem 2rem 3rem;
    }

    /* Remove all top spacing */
    /* Title styling */
    h1 {
        background: linear-gradient(135deg, #3b82f6 0%, #2563eb 100%);
        -webkit-background-clip: text;
        -webkit-text-fill-color: transparent;
        background-clip: text;
        font-size: 3rem !important;
        font-weight: 800 !important;
        margin-top: 0 !important;
        padding-top: 1rem !important;
        padding-bottom: 0.5rem !important;
        margin-bottom: 1.5rem !important;
        text-align: center !important;
        letter-spacing: -0.02em !important;
        border-bottom: 3px solid #e2e8f0;
    }

    /* Tab styling */
    .stTabs [data-baseweb="tab-list"] {
        gap: 0.5rem;
        background-color: #f8fafc;
        padding: 0.5rem;
        border-radius: 0.5rem;
    }

    .stTabs [data-baseweb="tab"] {
        height: 3rem;
        background-color: white;
        border-radius: 0.375rem;
        color: #64748b;
        font-weight: 500;
        border: 1px solid #e2e8f0;
        padding-left: 1.5rem;
        padding-right: 1.5rem;
    }

    .stTabs [data-baseweb="tab"]:hover {
        background-color: #f1f5f9;
        border-color: #cbd5e1;
    }

    .stTabs [aria-selected="true"] {
        background-color: #3b82f6 !important;
        color: white !important;
        border-color: #3b82f6 !important;
    }

    /* Subheader styling */
    h3 {
        color: #334155;
        font-weight: 600 !important;
        margin-top: 1.5rem !important;
    }

    /* Caption styling */
    [data-testid="stCaption"] {
        color: #64748b;
        font-size: 0.875rem;
    }

    /* Button styling */
    .stButton > button {
        background-color: #3b82f6;
        color: white;
        border: none;
        border-radius: 0.5rem;
        padding: 0.5rem 2rem;
        font-weight: 500;
        transition: all 0.2s;
    }

    .stButton > button:hover {
        background-color: #2563eb;
        box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.1);
    }

    /* Dataframe styling */
    .stDataFrame {
        border: 1px solid #e2e8f0;
        border-radius: 0.5rem;
        overflow: hidden;
    }

    /* HTML table styling for inventory */
    table {
        width: 100%;
        border-collapse: collapse;
        font-family: inherit;
    }
    table th {
        background-color: #f8fafc;
        padding: 0.75rem 1rem;
        text-align: left;
        font-weight: 600;
        color: #334155;
        border-bottom: 2px solid #e2e8f0;
    }
    table td {
        padding: 0.5rem 1rem;
        border-bottom: 1px solid #e2e8f0;
    }
    table tr:hover {
        filter: brightness(0.97);
    }
</style>
""", unsafe_allow_html=True)

# Auto-refresh every 2 seconds
st_autorefresh_interval = 2000  # milliseconds

# Add custom auto-refresh component using HTML/JavaScript
st.markdown(
    f"""
    <script>
        setTimeout(function() {{
            window.parent.location.reload();
        }}, {st_autorefresh_interval});
    </script>
    """,
    unsafe_allow_html=True
)

# Display banner image
pano_path = os.path.join(INVENTORY_DIR, "pano.png")
st.title("Minecraft Server Companion")

# Find all CSV files in the directory
csv_files = glob.glob(os.path.join(INVENTORY_DIR, "*.csv"))

# Shulker box color mapping (Minecraft dye colors to CSS colors)
# Each color has a 'dark' version for the shulker box row and 'light' for contents
SHULKER_COLORS = {
    'White': {'dark': 'rgba(249, 255, 254, 0.5)', 'light': 'rgba(249, 255, 254, 0.2)'},
    'Orange': {'dark': 'rgba(249, 128, 29, 0.5)', 'light': 'rgba(249, 128, 29, 0.2)'},
    'Magenta': {'dark': 'rgba(199, 78, 189, 0.5)', 'light': 'rgba(199, 78, 189, 0.2)'},
    'Light Blue': {'dark': 'rgba(58, 179, 218, 0.5)', 'light': 'rgba(58, 179, 218, 0.2)'},
    'Yellow': {'dark': 'rgba(254, 216, 61, 0.5)', 'light': 'rgba(254, 216, 61, 0.2)'},
    'Lime': {'dark': 'rgba(128, 199, 31, 0.5)', 'light': 'rgba(128, 199, 31, 0.2)'},
    'Pink': {'dark': 'rgba(243, 139, 170, 0.5)', 'light': 'rgba(243, 139, 170, 0.2)'},
    'Gray': {'dark': 'rgba(71, 79, 82, 0.5)', 'light': 'rgba(71, 79, 82, 0.2)'},
    'Light Gray': {'dark': 'rgba(157, 157, 151, 0.5)', 'light': 'rgba(157, 157, 151, 0.2)'},
    'Cyan': {'dark': 'rgba(22, 156, 156, 0.5)', 'light': 'rgba(22, 156, 156, 0.2)'},
    'Purple': {'dark': 'rgba(137, 50, 184, 0.5)', 'light': 'rgba(137, 50, 184, 0.2)'},
    'Blue': {'dark': 'rgba(60, 68, 170, 0.5)', 'light': 'rgba(60, 68, 170, 0.2)'},
    'Brown': {'dark': 'rgba(131, 84, 50, 0.5)', 'light': 'rgba(131, 84, 50, 0.2)'},
    'Green': {'dark': 'rgba(94, 124, 22, 0.5)', 'light': 'rgba(94, 124, 22, 0.2)'},
    'Red': {'dark': 'rgba(176, 46, 38, 0.5)', 'light': 'rgba(176, 46, 38, 0.2)'},
    'Black': {'dark': 'rgba(29, 29, 33, 0.5)', 'light': 'rgba(29, 29, 33, 0.2)'},
}

def get_shulker_color(material_name):
    """Extract the color dict from a shulker box material name."""
    if not isinstance(material_name, str):
        return None
    if 'Shulker Box' not in material_name:
        return None
    # "Shulker Box" alone (no color prefix) is purple
    if material_name.strip() == 'Shulker Box':
        return SHULKER_COLORS['Purple']
    # Extract color from "Color Shulker Box"
    for color in SHULKER_COLORS:
        if material_name.startswith(color):
            return SHULKER_COLORS[color]
    return SHULKER_COLORS['Purple']  # Default to purple

if csv_files:
    # Create a dictionary of player names to file paths
    player_data = {}
    for csv_path in csv_files:
        player_name = os.path.splitext(os.path.basename(csv_path))[0]
        player_data[player_name] = csv_path

    # Sort player names: scan_results first, villager_menu last, players in between
    def sort_key(name):
        if name == "scan_results":
            return (0, name)  # First
        elif name == "villager_menu":
            return (2, name)  # Last
        else:
            return (1, name)  # Middle, sorted alphabetically

    player_names = sorted(player_data.keys(), key=sort_key)

    # Create display names for tabs
    tab_names = []
    for name in player_names:
        if name == "scan_results":
            tab_names.append("Master Inventory")
        elif name == "villager_menu":
            tab_names.append("Villager Menu")
        else:
            tab_names.append(name)

    # Create tabs for each player
    tabs = st.tabs(tab_names)

    for i, player_name in enumerate(player_names):
        with tabs[i]:
            csv_path = player_data[player_name]

            # Show last updated time for this player
            if os.path.exists(csv_path):
                mod_timestamp = os.path.getmtime(csv_path)
                mod_time = time.ctime(mod_timestamp)
                st.caption(f"Last updated: {mod_time}")

                try:
                    df = pd.read_csv(csv_path, skiprows=1)

                    # Track row styling: {idx: {'color': css_color, 'is_parent': bool}}
                    row_styles = {}

                    if 'Count' in df.columns and 'ID' in df.columns:
                        # Sort by count, but keep shulker contents grouped with their shulker
                        # Shulker boxes have non-null IDs, their contents share the same ID
                        regular_items = df[df['ID'].isna()].copy()
                        shulker_items = df[df['ID'].notna()].copy()

                        # Sort regular items by count
                        regular_items = regular_items.sort_values(by='Count', ascending=False)

                        # Group shulker items by ID, sort groups by shulker box count
                        shulker_groups = []
                        for shulker_id in shulker_items['ID'].unique():
                            group = shulker_items[shulker_items['ID'] == shulker_id].copy()
                            # Find the shulker box row to get its count and color
                            shulker_box = group[group['Material'].str.contains('Shulker Box', case=False, na=False)]
                            sort_count = shulker_box['Count'].iloc[0] if not shulker_box.empty else 0
                            shulker_color = get_shulker_color(shulker_box['Material'].iloc[0]) if not shulker_box.empty else None
                            # Sort within group: shulker box first, then contents by count
                            group['_is_shulker'] = group['Material'].str.contains('Shulker Box', case=False, na=False)
                            group = group.sort_values(by=['_is_shulker', 'Count'], ascending=[False, False])
                            group = group.drop(columns=['_is_shulker'])
                            shulker_groups.append((sort_count, group, shulker_color))

                        # Sort shulker groups by their shulker box count
                        shulker_groups.sort(key=lambda x: x[0], reverse=True)

                        # Combine: regular items then shulker groups
                        df = pd.concat([regular_items] + [g[1] for g in shulker_groups], ignore_index=True)

                        # Color empty shulker boxes in regular items
                        for idx in range(len(regular_items)):
                            material = df.at[idx, 'Material']
                            shulker_color = get_shulker_color(material)
                            if shulker_color:
                                row_styles[idx] = {
                                    'color': shulker_color['dark'],
                                    'is_parent': True
                                }

                        # Build row style mapping and indent child items for populated shulkers
                        current_idx = len(regular_items)
                        for _, group, color in shulker_groups:
                            if color:
                                for i in range(len(group)):
                                    idx = current_idx + i
                                    is_parent = (i == 0)  # First row is the shulker box
                                    row_styles[idx] = {
                                        'color': color['dark'] if is_parent else color['light'],
                                        'is_parent': is_parent
                                    }
                                    if is_parent:
                                        # Clear count for populated shulker boxes
                                        df.at[idx, 'Count'] = ''
                                    else:
                                        # Indent child items in Material column
                                        df.at[idx, 'Material'] = '    ' + str(df.at[idx, 'Material'])
                            current_idx += len(group)

                    elif 'Count' in df.columns:
                        df = df.sort_values(by='Count', ascending=False)

                    # Hide ID column if present
                    if 'ID' in df.columns:
                        df = df.drop(columns=['ID'])

                    # Apply row styling for shulker colors
                    def style_rows(row):
                        idx = row.name
                        if idx in row_styles:
                            return [f'background-color: {row_styles[idx]["color"]}'] * len(row)
                        return [''] * len(row)

                    styled_df = df.style.apply(style_rows, axis=1)

                    # Render as HTML table to prevent sorting (st.dataframe sorting breaks grouping)
                    styled_html = styled_df.hide(axis='index').to_html()

                    # Wrap in a scrollable div if many rows
                    row_count = len(df)
                    if row_count > 15:
                        st.markdown(f'<div style="height: 600px; overflow-y: auto;">{styled_html}</div>', unsafe_allow_html=True)
                    else:
                        st.markdown(styled_html, unsafe_allow_html=True)

                except Exception as e:
                    st.error(f"Error reading CSV for {player_name}: {e}")
else:
    st.warning("Waiting for data...")
    st.code(f"No CSV files found at:\n{INVENTORY_DIR}")
    st.info("Make sure 'viewer.py' is in your main Server folder (next to server.jar).")


