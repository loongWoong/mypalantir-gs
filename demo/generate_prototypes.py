import yaml
import os

# Configuration
SCHEMA_PATH = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), 'ontology', 'schema.yaml')
OUTPUT_DIR = os.path.dirname(os.path.abspath(__file__))

# Tailwind CDN and Base Styles
HEAD_CONTENT = """
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<script src="https://cdn.tailwindcss.com"></script>
<link href="https://fonts.googleapis.com/css2?family=Inter:wght@300;400;500;600;700&display=swap" rel="stylesheet">
<style>
    body { font-family: 'Inter', sans-serif; }
</style>
<script>
    tailwind.config = {
        theme: {
            extend: {
                colors: {
                    primary: '#3b82f6',
                    secondary: '#64748b',
                }
            }
        }
    }
</script>
"""

NAVBAR = """
<nav class="bg-white border-b border-gray-200 sticky top-0 z-50">
    <div class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div class="flex justify-between h-16">
            <div class="flex">
                <div class="flex-shrink-0 flex items-center">
                    <span class="text-xl font-bold text-slate-800">Ontology Demo</span>
                </div>
                <div class="hidden sm:ml-6 sm:flex sm:space-x-8">
                    <a href="index.html" class="border-transparent text-gray-500 hover:border-gray-300 hover:text-gray-700 inline-flex items-center px-1 pt-1 border-b-2 text-sm font-medium">
                        Home
                    </a>
                </div>
            </div>
        </div>
    </div>
</nav>
"""

def load_schema():
    with open(SCHEMA_PATH, 'r', encoding='utf-8') as f:
        return yaml.safe_load(f)

def generate_profile_page(obj):
    filename = os.path.basename(obj['url'])
    filepath = os.path.join(OUTPUT_DIR, filename)
    
    properties_html = ""
    for prop in obj.get('properties', []):
        required_badge = '<span class="inline-flex items-center px-2 py-0.5 rounded text-xs font-medium bg-red-100 text-red-800">Required</span>' if prop.get('required') else ''
        properties_html += f"""
        <tr class="hover:bg-slate-50 transition-colors">
            <td class="whitespace-nowrap py-4 pl-4 pr-3 text-sm font-medium text-slate-900 sm:pl-6">{prop['name']}</td>
            <td class="whitespace-nowrap px-3 py-4 text-sm text-slate-500">{prop.get('display_name', '-')}</td>
            <td class="whitespace-nowrap px-3 py-4 text-sm text-slate-500">
                <span class="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium bg-blue-100 text-blue-800">
                    {prop['data_type']}
                </span>
            </td>
            <td class="whitespace-nowrap px-3 py-4 text-sm text-slate-500">{required_badge}</td>
            <td class="px-3 py-4 text-sm text-slate-500">{prop.get('description', '-')}</td>
        </tr>
        """

    html = f"""
<!DOCTYPE html>
<html lang="en">
<head>
    <title>{obj.get('display_name')} - Profile</title>
    {HEAD_CONTENT}
</head>
<body class="bg-slate-50 min-h-screen">
    {NAVBAR}
    
    <div class="py-10">
        <header>
            <div class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
                <h1 class="text-3xl font-bold leading-tight text-slate-900">{obj.get('display_name')} <span class="text-slate-400 text-lg font-normal">({obj['name']})</span></h1>
                <p class="mt-2 text-sm text-slate-600 max-w-2xl">{obj.get('description')}</p>
            </div>
        </header>
        
        <main>
            <div class="max-w-7xl mx-auto sm:px-6 lg:px-8">
                
                <!-- Stats Section -->
                <div class="mt-8 grid grid-cols-1 gap-5 sm:grid-cols-3">
                    <div class="bg-white overflow-hidden shadow rounded-lg">
                        <div class="px-4 py-5 sm:p-6">
                            <dt class="text-sm font-medium text-gray-500 truncate">Total Records</dt>
                            <dd class="mt-1 text-3xl font-semibold text-gray-900">128,492</dd>
                        </div>
                    </div>
                    <div class="bg-white overflow-hidden shadow rounded-lg">
                        <div class="px-4 py-5 sm:p-6">
                            <dt class="text-sm font-medium text-gray-500 truncate">Last Updated</dt>
                            <dd class="mt-1 text-3xl font-semibold text-gray-900">Just now</dd>
                        </div>
                    </div>
                    <div class="bg-white overflow-hidden shadow rounded-lg">
                        <div class="px-4 py-5 sm:p-6">
                            <dt class="text-sm font-medium text-gray-500 truncate">Data Quality Score</dt>
                            <dd class="mt-1 text-3xl font-semibold text-green-600">98.5%</dd>
                        </div>
                    </div>
                </div>

                <!-- Properties Table -->
                <div class="mt-8 flex flex-col">
                    <div class="-my-2 -mx-4 overflow-x-auto sm:-mx-6 lg:-mx-8">
                        <div class="inline-block min-w-full py-2 align-middle md:px-6 lg:px-8">
                            <div class="overflow-hidden shadow ring-1 ring-black ring-opacity-5 md:rounded-lg bg-white">
                                <div class="px-4 py-5 sm:px-6 border-b border-gray-200 bg-gray-50">
                                    <h3 class="text-lg leading-6 font-medium text-gray-900">Schema Properties</h3>
                                    <p class="mt-1 max-w-2xl text-sm text-gray-500">Details about the data fields defined for this object.</p>
                                </div>
                                <table class="min-w-full divide-y divide-gray-300">
                                    <thead class="bg-gray-50">
                                        <tr>
                                            <th scope="col" class="py-3.5 pl-4 pr-3 text-left text-sm font-semibold text-gray-900 sm:pl-6">Name</th>
                                            <th scope="col" class="px-3 py-3.5 text-left text-sm font-semibold text-gray-900">Display Name</th>
                                            <th scope="col" class="px-3 py-3.5 text-left text-sm font-semibold text-gray-900">Type</th>
                                            <th scope="col" class="px-3 py-3.5 text-left text-sm font-semibold text-gray-900">Required</th>
                                            <th scope="col" class="px-3 py-3.5 text-left text-sm font-semibold text-gray-900">Description</th>
                                        </tr>
                                    </thead>
                                    <tbody class="divide-y divide-gray-200 bg-white">
                                        {properties_html}
                                    </tbody>
                                </table>
                            </div>
                        </div>
                    </div>
                </div>
                
            </div>
        </main>
    </div>
</body>
</html>
    """
    
    with open(filepath, 'w', encoding='utf-8') as f:
        f.write(html)
    print(f"Generated {filename}")

def generate_process_page(link, object_map):
    filename = os.path.basename(link['url'])
    filepath = os.path.join(OUTPUT_DIR, filename)
    
    source_obj = object_map.get(link['source_type'], {})
    target_obj = object_map.get(link['target_type'], {})
    
    mappings_html = ""
    for src, tgt in link.get('property_mappings', {}).items():
        mappings_html += f"""
        <div class="flex items-center justify-between py-3 border-b border-gray-100 last:border-0">
            <div class="flex items-center space-x-2 w-5/12">
                <span class="px-2 py-1 bg-blue-50 text-blue-700 text-xs rounded font-mono">{src}</span>
                <span class="text-xs text-gray-500 truncate">({source_obj.get('display_name', 'Unknown')})</span>
            </div>
            <div class="flex-shrink-0 text-gray-400">
                <svg class="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M17 8l4 4m0 0l-4 4m4-4H3" />
                </svg>
            </div>
            <div class="flex items-center justify-end space-x-2 w-5/12 text-right">
                <span class="text-xs text-gray-500 truncate">({target_obj.get('display_name', 'Unknown')})</span>
                <span class="px-2 py-1 bg-green-50 text-green-700 text-xs rounded font-mono">{tgt}</span>
            </div>
        </div>
        """

    html = f"""
<!DOCTYPE html>
<html lang="en">
<head>
    <title>{link.get('display_name')} - Process</title>
    {HEAD_CONTENT}
</head>
<body class="bg-slate-50 min-h-screen">
    {NAVBAR}
    
    <div class="py-10">
        <header>
            <div class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
                <div class="flex items-center space-x-3">
                     <span class="inline-flex items-center px-3 py-1 rounded-full text-xs font-medium bg-purple-100 text-purple-800">Process</span>
                     <h1 class="text-3xl font-bold leading-tight text-slate-900">{link.get('display_name')}</h1>
                </div>
                <p class="mt-2 text-sm text-slate-600 max-w-2xl">{link.get('description')}</p>
            </div>
        </header>
        
        <main>
            <div class="max-w-7xl mx-auto sm:px-6 lg:px-8 mt-10">
                
                <!-- Flow Diagram -->
                <div class="relative">
                    <div class="absolute inset-0 flex items-center" aria-hidden="true">
                        <div class="w-full border-t border-gray-300"></div>
                    </div>
                    <div class="relative flex justify-center">
                        <span class="px-3 bg-slate-50 text-lg font-medium text-gray-900">Data Flow</span>
                    </div>
                </div>

                <div class="mt-8 flex flex-col md:flex-row items-stretch justify-between gap-8">
                    
                    <!-- Source -->
                    <div class="flex-1 bg-white overflow-hidden shadow rounded-lg border-t-4 border-blue-500">
                        <div class="px-4 py-5 sm:px-6 bg-gray-50">
                            <h3 class="text-lg leading-6 font-medium text-gray-900">Source</h3>
                            <p class="mt-1 max-w-2xl text-sm text-gray-500">{link.get('source_type')}</p>
                        </div>
                        <div class="px-4 py-5 sm:p-6">
                            <div class="text-sm text-gray-900 font-medium">{source_obj.get('display_name')}</div>
                            <div class="mt-2 text-xs text-gray-500">{source_obj.get('description')}</div>
                            <div class="mt-4">
                                <a href="{os.path.basename(source_obj.get('url', '#'))}" class="text-blue-600 hover:text-blue-900 text-sm font-medium">View Profile &rarr;</a>
                            </div>
                        </div>
                    </div>

                    <!-- Transformation Logic -->
                    <div class="flex-none flex flex-col items-center justify-center space-y-2 px-4">
                        <div class="p-3 bg-purple-100 rounded-full">
                            <svg class="h-8 w-8 text-purple-600" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19.428 15.428a2 2 0 00-1.022-.547l-2.384-.477a6 6 0 00-3.86.517l-.318.158a6 6 0 01-3.86.517L6.05 15.21a2 2 0 00-1.806.547M8 4h8l-1 1v5.172a2 2 0 00.586 1.414l5 5c1.26 1.26.367 3.414-1.415 3.414H4.828c-1.782 0-2.674-2.154-1.414-3.414l5-5A2 2 0 009 10.172V5L8 4z" />
                            </svg>
                        </div>
                        <span class="text-xs font-medium text-gray-500">{link.get('cardinality')}</span>
                        <span class="text-xs font-medium text-gray-500 bg-gray-100 px-2 py-1 rounded">{link.get('direction')}</span>
                    </div>

                    <!-- Target -->
                    <div class="flex-1 bg-white overflow-hidden shadow rounded-lg border-t-4 border-green-500">
                        <div class="px-4 py-5 sm:px-6 bg-gray-50">
                            <h3 class="text-lg leading-6 font-medium text-gray-900">Target</h3>
                            <p class="mt-1 max-w-2xl text-sm text-gray-500">{link.get('target_type')}</p>
                        </div>
                        <div class="px-4 py-5 sm:p-6">
                            <div class="text-sm text-gray-900 font-medium">{target_obj.get('display_name')}</div>
                            <div class="mt-2 text-xs text-gray-500">{target_obj.get('description')}</div>
                            <div class="mt-4">
                                <a href="{os.path.basename(target_obj.get('url', '#'))}" class="text-green-600 hover:text-green-900 text-sm font-medium">View Profile &rarr;</a>
                            </div>
                        </div>
                    </div>
                </div>

                <!-- Mappings -->
                <div class="mt-10 bg-white shadow overflow-hidden sm:rounded-lg">
                    <div class="px-4 py-5 sm:px-6 border-b border-gray-200">
                        <h3 class="text-lg leading-6 font-medium text-gray-900">Field Mappings</h3>
                        <p class="mt-1 max-w-2xl text-sm text-gray-500">How data is transformed and mapped from source to target.</p>
                    </div>
                    <div class="px-4 py-5 sm:p-6">
                        <div class="flex flex-col">
                            {mappings_html}
                        </div>
                    </div>
                </div>

            </div>
        </main>
    </div>
</body>
</html>
    """
    
    with open(filepath, 'w', encoding='utf-8') as f:
        f.write(html)
    print(f"Generated {filename}")

def generate_index_page(objects, links):
    filepath = os.path.join(OUTPUT_DIR, 'index.html')
    
    obj_list = ""
    for obj in objects:
        filename = os.path.basename(obj['url'])
        obj_list += f"""
        <a href="{filename}" class="group block p-6 bg-white shadow rounded-lg hover:shadow-md transition-shadow">
            <div class="flex items-center justify-between">
                <div class="flex items-center">
                    <div class="p-2 bg-blue-50 rounded-lg group-hover:bg-blue-100 transition-colors">
                        <svg class="h-6 w-6 text-blue-600" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 11H5m14 0a2 2 0 012 2v6a2 2 0 01-2 2H5a2 2 0 01-2-2v-6a2 2 0 012-2m14 0V9a2 2 0 00-2-2M5 11V9a2 2 0 012-2m0 0V5a2 2 0 012-2h6a2 2 0 012 2v2M7 7h10" />
                        </svg>
                    </div>
                    <div class="ml-4">
                        <h3 class="text-lg font-medium text-slate-900">{obj['display_name']}</h3>
                        <p class="text-sm text-slate-500">{obj['name']}</p>
                    </div>
                </div>
                <svg class="h-5 w-5 text-gray-400 group-hover:text-gray-500" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 5l7 7-7 7" />
                </svg>
            </div>
        </a>
        """

    link_list = ""
    for link in links:
        filename = os.path.basename(link['url'])
        link_list += f"""
        <a href="{filename}" class="group block p-6 bg-white shadow rounded-lg hover:shadow-md transition-shadow">
            <div class="flex items-center justify-between">
                <div class="flex items-center">
                    <div class="p-2 bg-purple-50 rounded-lg group-hover:bg-purple-100 transition-colors">
                        <svg class="h-6 w-6 text-purple-600" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M13 10V3L4 14h7v7l9-11h-7z" />
                        </svg>
                    </div>
                    <div class="ml-4">
                        <h3 class="text-lg font-medium text-slate-900">{link['display_name']}</h3>
                        <p class="text-sm text-slate-500">{link['name']}</p>
                    </div>
                </div>
                <svg class="h-5 w-5 text-gray-400 group-hover:text-gray-500" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 5l7 7-7 7" />
                </svg>
            </div>
        </a>
        """

    html = f"""
<!DOCTYPE html>
<html lang="en">
<head>
    <title>Ontology Demo Index</title>
    {HEAD_CONTENT}
</head>
<body class="bg-slate-50 min-h-screen">
    {NAVBAR}
    
    <div class="py-10">
        <header>
            <div class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
                <h1 class="text-3xl font-bold leading-tight text-slate-900">Project Overview</h1>
                <p class="mt-2 text-sm text-slate-600">Explore the generated prototypes for objects and processes.</p>
            </div>
        </header>
        
        <main>
            <div class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
                
                <div class="mt-10">
                    <h2 class="text-xl font-bold text-slate-900 mb-5">Object Profiles</h2>
                    <div class="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
                        {obj_list}
                    </div>
                </div>

                <div class="mt-12">
                    <h2 class="text-xl font-bold text-slate-900 mb-5">Link Processes</h2>
                    <div class="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
                        {link_list}
                    </div>
                </div>

            </div>
        </main>
    </div>
</body>
</html>
    """
    
    with open(filepath, 'w', encoding='utf-8') as f:
        f.write(html)
    print("Generated index.html")

def main():
    print(f"Loading schema from {SCHEMA_PATH}")
    schema = load_schema()
    
    objects = schema.get('object_types', [])
    links = schema.get('link_types', [])
    
    # Create a map for easy lookup
    object_map = {obj['name']: obj for obj in objects}
    
    print(f"Found {len(objects)} objects and {len(links)} links.")
    
    for obj in objects:
        generate_profile_page(obj)
        
    for link in links:
        generate_process_page(link, object_map)
        
    generate_index_page(objects, links)
    print("All done!")

if __name__ == "__main__":
    main()
