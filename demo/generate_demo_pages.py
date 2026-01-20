import os
import re

# File paths
schema_path = r'g:\mypalantir-gs\ontology\schema.yaml'
demo_dir = r'g:\mypalantir-gs\demo'

if not os.path.exists(demo_dir):
    os.makedirs(demo_dir)

# Data structures to hold parsed info
object_types = {}
link_types = []

# --- Parsing Schema (Simple Line-based Parser) ---
with open(schema_path, 'r', encoding='utf-8') as f:
    lines = f.readlines()

current_section = None
current_obj = None
current_link = None
current_prop = None

for line in lines:
    stripped = line.strip()
    if not stripped:
        continue
        
    # Section detection
    if stripped.startswith('object_types:'):
        current_section = 'object_types'
        continue
    elif stripped.startswith('link_types:'):
        current_section = 'link_types'
        continue
    
    # Object Types Parsing
    if current_section == 'object_types':
        obj_match = re.match(r'^\s+-\s+name:\s+(\w+)', line)
        if obj_match:
            current_obj = {
                'name': obj_match.group(1),
                'display_name': '',
                'description': '',
                'properties': []
            }
            object_types[current_obj['name']] = current_obj
            current_link = None
            continue
        
        if current_obj:
            # Object Metadata
            dn_match = re.match(r'^\s+display_name:\s+(.+)', line)
            if dn_match:
                current_obj['display_name'] = dn_match.group(1).strip()
            
            desc_match = re.match(r'^\s+description:\s+"?(.+?)"?$', line)
            if desc_match:
                current_obj['description'] = desc_match.group(1).strip()

            # Property Detection
            prop_match = re.match(r'^\s+-\s+name:\s+(\w+)', line)
            if prop_match:
                current_prop = {
                    'name': prop_match.group(1),
                    'display_name': '',
                    'data_type': 'string',
                    'description': ''
                }
                current_obj['properties'].append(current_prop)
                continue
            
            if current_prop:
                p_dn_match = re.match(r'^\s+display_name:\s+(.+)', line)
                if p_dn_match:
                    current_prop['display_name'] = p_dn_match.group(1).strip()
                
                p_type_match = re.match(r'^\s+data_type:\s+(\w+)', line)
                if p_type_match:
                    current_prop['data_type'] = p_type_match.group(1).strip()
                
                p_desc_match = re.match(r'^\s+description:\s+"?(.+?)"?$', line)
                if p_desc_match:
                    current_prop['description'] = p_desc_match.group(1).strip()

    # Link Types Parsing
    elif current_section == 'link_types':
        link_match = re.match(r'^\s+-\s+name:\s+(\w+)', line)
        if link_match:
            current_link = {
                'name': link_match.group(1),
                'display_name': '',
                'description': '',
                'source_type': '',
                'target_type': '',
                'mappings': {}
            }
            link_types.append(current_link)
            current_obj = None
            continue
        
        if current_link:
            dn_match = re.match(r'^\s+display_name:\s+(.+)', line)
            if dn_match:
                current_link['display_name'] = dn_match.group(1).strip()
            
            desc_match = re.match(r'^\s+description:\s+"?(.+?)"?$', line)
            if desc_match:
                current_link['description'] = desc_match.group(1).strip()
            
            src_match = re.match(r'^\s+source_type:\s+(\w+)', line)
            if src_match:
                current_link['source_type'] = src_match.group(1).strip()
            
            tgt_match = re.match(r'^\s+target_type:\s+(\w+)', line)
            if tgt_match:
                current_link['target_type'] = tgt_match.group(1).strip()
            
            # Mapping (Simple detection)
            map_match = re.match(r'^\s+(\w+):\s+(\w+)', line)
            if map_match and 'property_mappings' not in line:
                 current_link['mappings'][map_match.group(1)] = map_match.group(2)


# --- HTML Templates ---

# Shared CSS
STYLE = """
<style>
    :root {
        --primary: #106ebe;
        --bg: #f3f2f1;
        --card-bg: #ffffff;
        --text: #323130;
        --border: #e1dfdd;
    }
    body { font-family: 'Segoe UI', 'Segoe UI Web (West European)', 'Segoe UI', -apple-system, BlinkMacSystemFont, Roboto, 'Helvetica Neue', sans-serif; background-color: var(--bg); color: var(--text); margin: 0; padding: 20px; }
    .header { background: var(--card-bg); padding: 20px; border-radius: 4px; box-shadow: 0 2px 4px rgba(0,0,0,0.1); margin-bottom: 20px; border-left: 5px solid var(--primary); }
    .header h1 { margin: 0; font-size: 24px; color: var(--primary); }
    .header p { margin: 5px 0 0; color: #605e5c; }
    
    .grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(300px, 1fr)); gap: 20px; }
    .card { background: var(--card-bg); border-radius: 4px; padding: 15px; box-shadow: 0 1.6px 3.6px rgba(0,0,0,0.13), 0 0.3px 0.9px rgba(0,0,0,0.11); }
    .card h3 { margin-top: 0; border-bottom: 1px solid var(--border); padding-bottom: 10px; font-size: 16px; color: #0078d4; }
    
    .prop-row { display: flex; justify-content: space-between; padding: 8px 0; border-bottom: 1px solid #f3f2f1; }
    .prop-row:last-child { border-bottom: none; }
    .prop-key { font-weight: 600; color: #605e5c; font-size: 14px; }
    .prop-val { color: #201f1e; font-size: 14px; }
    
    .process-flow { display: flex; align-items: center; justify-content: center; background: white; padding: 40px; margin-bottom: 20px; border-radius: 4px; }
    .node { background: #eff6fc; border: 2px solid #0078d4; padding: 15px 30px; border-radius: 6px; font-weight: bold; }
    .arrow { margin: 0 20px; font-size: 24px; color: #605e5c; }
    .processor { background: #fdf6f6; border: 2px solid #d13438; padding: 15px; border-radius: 50%; width: 60px; height: 60px; display: flex; align-items: center; justify-content: center; font-size: 12px; text-align: center; }
    
    .log-console { background: #1e1e1e; color: #d4d4d4; padding: 15px; border-radius: 4px; font-family: 'Consolas', 'Monaco', monospace; height: 300px; overflow-y: auto; font-size: 13px; }
    .log-entry { margin-bottom: 5px; border-bottom: 1px solid #333; padding-bottom: 2px; }
    .ts { color: #569cd6; margin-right: 10px; }
    .info { color: #4ec9b0; }
</style>
"""

NAV_BAR = """
<div style="background: #0078d4; padding: 10px; margin: -20px -20px 20px -20px; color: white; display: flex; gap: 20px;">
    <a href="/demo/" style="color: white; text-decoration: none; font-weight: bold;">Home</a>
    <span>Highway Toll System Demo</span>
</div>
"""

# --- Generators ---

def generate_mock_value(prop):
    dt = prop['data_type']
    name = prop['name'].lower()
    
    if 'id' in name:
        return f"UUID-{hash(name)%10000:04d}"
    if 'time' in name or 'date' in name:
        return "2024-05-20 14:30:00"
    if 'plate' in name or 'vlp' in name:
        return "京A88888"
    if 'fee' in name or 'money' in name or 'amount' in name:
        return "¥ 25.00"
    if dt == 'integer':
        return "1"
    if dt == 'double' or dt == 'bigdecimal':
        return "0.95"
    return "Sample Text"

def create_object_page(obj):
    filename = f"{obj['name']}_profile.html"
    filepath = os.path.join(demo_dir, filename)
    
    props_html = ""
    for prop in obj['properties']:
        val = generate_mock_value(prop)
        props_html += f"""
        <div class="prop-row">
            <span class="prop-key" title="{prop['description']}">{prop['display_name'] or prop['name']}</span>
            <span class="prop-val">{val}</span>
        </div>
        """
    
    html = f"""<!DOCTYPE html>
<html>
<head>
    <title>{obj['display_name']} - Profile</title>
    <meta charset="utf-8">
    {STYLE}
</head>
<body>
    {NAV_BAR}
    <div class="header">
        <h1>{obj['display_name']} <small style="font-size: 0.6em; color: #666;">({obj['name']})</small></h1>
        <p>{obj['description']}</p>
    </div>
    
    <div class="grid">
        <div class="card">
            <h3>Basic Information</h3>
            {props_html}
        </div>
        
        <div class="card">
            <h3>System Metadata</h3>
            <div class="prop-row"><span class="prop-key">Sync Status</span><span class="prop-val" style="color: green;">● Synced</span></div>
            <div class="prop-row"><span class="prop-key">Last Updated</span><span class="prop-val">Just Now</span></div>
            <div class="prop-row"><span class="prop-key">Source System</span><span class="prop-val">Toll-Core-DB</span></div>
            <div class="prop-row"><span class="prop-key">Data Quality</span><span class="prop-val">100%</span></div>
        </div>
        
        <div class="card" style="grid-column: span 2;">
            <h3>Recent Activity / Logs</h3>
            <div style="background: #faf9f8; padding: 10px; font-size: 12px; color: #666;">
                <p>[INFO] Record created successfully.</p>
                <p>[INFO] Validated against schema v1.0.</p>
                <p>[INFO] Indexing completed in 2ms.</p>
            </div>
        </div>
    </div>
</body>
</html>
"""
    with open(filepath, 'w', encoding='utf-8') as f:
        f.write(html)
    print(f"Generated {filename}")

def create_link_page(link):
    filename = f"{link['name']}_process.html"
    filepath = os.path.join(demo_dir, filename)
    
    # Mapping Table
    mapping_rows = ""
    for k, v in link['mappings'].items():
        mapping_rows += f"<tr><td style='padding:8px; border-bottom:1px solid #ddd;'>{k}</td><td style='padding:8px; border-bottom:1px solid #ddd;'>&rarr;</td><td style='padding:8px; border-bottom:1px solid #ddd;'>{v}</td></tr>"
    
    # Mock Logs
    src = link['source_type']
    tgt = link['target_type']
    logs = f"""
    <div class="log-entry"><span class="ts">10:00:01</span> <span class="info">INFO</span> Received event from <b>{src}</b> topic.</div>
    <div class="log-entry"><span class="ts">10:00:01</span> <span class="info">INFO</span> Applying transformation rules: {link['name']}...</div>
    <div class="log-entry"><span class="ts">10:00:02</span> <span class="info">INFO</span> Mapping fields successfully.</div>
    <div class="log-entry"><span class="ts">10:00:02</span> <span class="info">INFO</span> Persisting to <b>{tgt}</b> storage.</div>
    <div class="log-entry"><span class="ts">10:00:05</span> <span class="info">INFO</span> Batch processing complete. Awaiting new events.</div>
    """
    
    html = f"""<!DOCTYPE html>
<html>
<head>
    <title>{link['display_name']} - Processing</title>
    <meta charset="utf-8">
    {STYLE}
</head>
<body>
    {NAV_BAR}
    <div class="header" style="border-color: #d13438;">
        <h1>{link['display_name']} <small style="font-size: 0.6em; color: #666;">({link['name']})</small></h1>
        <p>{link['description']}</p>
        <p style="margin-top: 10px;">
            <span style="background: #e6ffec; color: #107c10; padding: 2px 8px; border-radius: 10px; font-size: 12px; font-weight: bold;">● RUNNING</span>
            <span style="margin-left: 10px; font-size: 12px;">Throughput: 1,200 events/sec</span>
        </p>
    </div>
    
    <div class="process-flow">
        <div class="node">{src}</div>
        <div class="arrow">&rarr;</div>
        <div class="processor" title="Flink Job / ETL Function">Process Logic</div>
        <div class="arrow">&rarr;</div>
        <div class="node">{tgt}</div>
    </div>
    
    <div class="grid">
        <div class="card">
            <h3>Mapping Logic</h3>
            <table style="width: 100%; border-collapse: collapse; font-size: 14px;">
                <thead>
                    <tr style="text-align: left; background: #f3f2f1;">
                        <th style="padding: 8px;">Source Field</th>
                        <th style="padding: 8px;"></th>
                        <th style="padding: 8px;">Target Field</th>
                    </tr>
                </thead>
                <tbody>
                    {mapping_rows}
                </tbody>
            </table>
        </div>
        
        <div class="card">
            <h3>Processing Metrics</h3>
            <div class="prop-row"><span class="prop-key">Total Processed</span><span class="prop-val">8,452,102</span></div>
            <div class="prop-row"><span class="prop-key">Average Latency</span><span class="prop-val">45 ms</span></div>
            <div class="prop-row"><span class="prop-key">Error Rate</span><span class="prop-val" style="color: red;">0.01%</span></div>
            <div class="prop-row"><span class="prop-key">Last Checkpoint</span><span class="prop-val">10:00:00</span></div>
        </div>
        
        <div class="card" style="grid-column: span 2;">
            <h3>Live System Logs</h3>
            <div class="log-console">
                {logs}
            </div>
        </div>
    </div>
</body>
</html>
"""
    with open(filepath, 'w', encoding='utf-8') as f:
        f.write(html)
    print(f"Generated {filename}")

# --- Main Execution ---

print(f"Found {len(object_types)} object types and {len(link_types)} link types.")

for obj in object_types.values():
    create_object_page(obj)

for link in link_types:
    create_link_page(link)

print("All pages generated successfully.")
