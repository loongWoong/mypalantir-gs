import os
import sys
from http.server import HTTPServer, SimpleHTTPRequestHandler

# Set the port
PORT = 8000

# Determine the project root directory (one level up from this script)
# Script location: g:\mypalantir-gs\demo\server.py
# Project root: g:\mypalantir-gs
current_dir = os.path.dirname(os.path.abspath(__file__))
project_root = os.path.dirname(current_dir)

class ProjectRootRequestHandler(SimpleHTTPRequestHandler):
    def __init__(self, *args, **kwargs):
        # Set the directory to serve from to the project root
        # This ensures that requests to /demo/... map to g:\mypalantir-gs\demo\...
        super().__init__(*args, directory=project_root, **kwargs)

    def end_headers(self):
        # Add CORS headers just in case they are needed for cross-origin requests
        self.send_header('Access-Control-Allow-Origin', '*')
        super().end_headers()

def run(server_class=HTTPServer, handler_class=ProjectRootRequestHandler):
    # Bind to 0.0.0.0 to allow access from other computers
    server_address = ('0.0.0.0', PORT)
    httpd = server_class(server_address, handler_class)
    print(f"Serving HTTP on 0.0.0.0 port {PORT} ...")
    print(f"You can access the demo at: http://<server_ip>:{PORT}/demo/")
    try:
        httpd.serve_forever()
    except KeyboardInterrupt:
        print("\nKeyboard interrupt received, exiting.")
        httpd.server_close()
        sys.exit(0)

if __name__ == '__main__':
    run()
