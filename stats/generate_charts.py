# Constants
LATEST_FILE_PATH = '2024-11_v190_sessions_grouped_by_users.json'
PIE_CHART_LIMIT = 8  # For the pie chart
TABLE_LIMIT = 20  # For the table
CHART_TYPE = "LANGUAGE"  # Options: "LANGUAGE", "OS", "JAVA"

# Re-import necessary libraries since the execution state was reset
import json
from collections import Counter
import numpy as np
import matplotlib.pyplot as plt
from matplotlib.colors import to_rgb
import random

# Function to determine if text color should be dark or light based on background color
def get_readable_text_color(background_color, light_color='#e8cc87', dark_color='#013a20'):
    color = to_rgb(background_color)
    if np.mean(color) > 0.5:
        return dark_color  # background is light, use dark text
    else:
        return light_color  # background is dark, use light text

# Load the latest JSON data from the uploaded file
with open(LATEST_FILE_PATH, 'r') as file:
    latest_data = json.load(file)

# Define a dictionary to map two-letter language codes to their full English names
language_names = {
    'en': 'English', 'de': 'German', 'ja': 'Japanese', 'pt': 'Portuguese',
    'hu': 'Hungarian', 'it': 'Italian', 'sv': 'Swedish', 'es': 'Spanish',
    'fr': 'French', 'tr': 'Turkish', 'zh': 'Chinese', 'ru': 'Russian',
    'nl': 'Dutch', 'pl': 'Polish', 'ko': 'Korean', 'uk': 'Ukrainian',
    'sk': 'Slovak', 'cs': 'Czech', 'da': 'Danish', 'fi': 'Finnish',
    'no': 'Norwegian', 'el': 'Greek', 'bg': 'Bulgarian', 'et': 'Estonian',
    'lv': 'Latvian', 'lt': 'Lithuanian', 'sl': 'Slovenian', 'hr': 'Croatian',
    'sr': 'Serbian', 'mk': 'Macedonian', 'bs': 'Bosnian', 'sq': 'Albanian',
    'ro': 'Romanian', 'ar': 'Arabic', 'he': 'Hebrew', 'fa': 'Persian',
    'hi': 'Hindi', 'th': 'Thai', 'id': 'Indonesian', 'vi': 'Vietnamese',
    'tl': 'Tagalog', 'mn': 'Mongolian'
}

os_names = {
    "Windows 10": "Windows",
    "Windows 11": "Windows",
    "Mac OS X": "Mac OS X",
    "Linux": "Linux"
}

java_names = {
    "17": "Java 17",
    "18": "Java 18",
    "19": "Java 19",
    "20": "Java 20",
    "21": "Java 21",
    "22": "Java 22",
    "23": "Java 23",
    "24": "Java 24"
}

# Extract data based on chart type
if CHART_TYPE == "LANGUAGE":
    data_key = 'system_language'
    data_name_map = language_names
    default_name = 'Other'
elif CHART_TYPE == "OS":
    data_key = 'os_name'
    data_name_map = os_names
    default_name = 'Unknown OS'
elif CHART_TYPE == "JAVA":
    data_key = 'java_version'
    data_name_map = java_names
    default_name = 'Unknown'
else:
    raise ValueError("Invalid CHART_TYPE specified.")

# If we are in Java version mode, we need to modify the data in data_counts so the minor version and build version are ignored
if CHART_TYPE == "JAVA":
    data_counts = Counter(
        entry.get(data_key, '').split('_')[0].split('.')[0].split('-')[0] for entry in latest_data if entry.get(data_key)
    )
else:
    data_counts = Counter(
        entry.get(data_key, '').split('_')[0] for entry in latest_data if entry.get(data_key)
    )

# Replace codes with full names if available
full_data_counts = Counter()
for code, count in data_counts.items():
    full_name = data_name_map.get(code, default_name)  # Default to 'Other' if the code is not recognized
    full_data_counts[full_name] += count

# Get the top data points for the pie chart and table separately
top_data_pie = full_data_counts.most_common(PIE_CHART_LIMIT + 1)
top_data_table = full_data_counts.most_common(TABLE_LIMIT + 1)

# Prepare data for the pie chart
top_data_pie_data = top_data_pie[:-1]  # Exclude 'Other'
other_count_pie = sum(count for name, count in full_data_counts.items() if name not in [name for name, _ in top_data_pie_data])
#top_data_pie_data.append(('Other', other_count_pie))

# Prepare data for the table
top_data_table_data = top_data_table[:-1]  # Exclude 'Other'
other_count_table = sum(count for name, count in full_data_counts.items() if name not in [name for name, _ in top_data_table_data])
top_data_table_data.append(('Other', other_count_table))

# Pie chart and table data
labels_pie, sizes_pie = zip(*top_data_pie_data)
labels_table, sizes_table = zip(*top_data_table_data)

# Generate a gradient of colors between golden yellow and dark green for the data points
colors = plt.cm.viridis(np.linspace(0, 1, len(labels_pie)))

# Pie chart parameters
explode = [0.1 if label == 'Other' else 0.0 for label in labels_pie]

# Use context manager for plotting
with plt.style.context('ggplot'):

    fig, (ax1, ax2) = plt.subplots(ncols=2, figsize=(19.2, 10.8), gridspec_kw={'width_ratios': [2, 1]})
    
    # Set the background color
    fig.patch.set_facecolor('#013a20')
    ax1.patch.set_facecolor('#013a20')
    ax2.patch.set_facecolor('#013a20')
    
    # Pie chart
pie_wedges, pie_texts, pie_autotexts = ax1.pie(
    sizes_pie, 
    labels=labels_pie, 
    startangle=90, 
    colors=colors, 
    explode=explode, 
    autopct='%1.1f%%'
)

# Randomize the positions of the text labels along the radius
radius_push = 0
for text in pie_texts:
    x, y = text.get_position()
    # Convert to polar coordinates
    r = np.sqrt(x**2 + y**2)
    theta = np.arctan2(y, x)
    # Randomize the radius slightly
    r += radius_push - 0.05
    radius_push = (radius_push + 0.05) % 0.15
    # Convert back to Cartesian coordinates
    x = r * np.cos(theta)
    y = r * np.sin(theta)
    text.set_position((x, y))
    text.set_color('#e8cc87')

# Change text color dynamically for the pie chart based on its background
for pie_wedge, pie_text in zip(pie_wedges, pie_autotexts):
    background_color = pie_wedge.get_facecolor()
    # Get a readable text color based on the background
    readable_text_color = get_readable_text_color(background_color)
    pie_text.set_color(readable_text_color)
            
    # Draw a table beside the pie chart
    table_data = [[label, f"{count}"] for label, count in top_data_table_data]
    table = ax2.table(cellText=table_data, colLabels=['Category', 'Count'], loc='center', cellLoc='center')
    table.auto_set_font_size(False)
    table.set_fontsize(10)
    table.scale(1, 1.5)
    
    # Format the header of the table
    for key, cell in table._cells.items():
        row, col = key
        cell.set_edgecolor('#e8cc87')  # Set the border color of all cells
        cell.set_facecolor('#013a20')
        if row == 0:  # Header cells are in row 0
            cell.set_text_props(weight='bold', color='#e8cc87')
            cell.set_facecolor('#013a20')
        else:  # For other cells in the table
            cell.set_text_props(color='#e8cc87')  # Set the text color
    
    # Formatting the axes
    ax1.axis('equal')
    ax2.axis('off')

# Save the figure
latest_pie_chart_full_path = f'{CHART_TYPE.lower()}_pie_chart.png'
plt.savefig(latest_pie_chart_full_path, facecolor=fig.get_facecolor(), bbox_inches='tight')
plt.close()  # Closing the plot to prevent it from displaying in the output

# Give some feedback on the console
print(f"{latest_pie_chart_full_path} was saved")

# Generate some statistics: total number of users, total number of sessions, and the number of user with higher than 3 sessions
total_users = len(latest_data)
total_sessions = sum(int(entry['session_count']) for entry in latest_data)
users_with_at_least_5_sessions = sum(1 for entry in latest_data if int(entry['session_count']) >= 5)

# Print out the statistics
print(f"Total number of users: {total_users}")
print(f"Total number of sessions: {total_sessions}")
print(f"Number of users with at least 5 sessions: {users_with_at_least_5_sessions}")
