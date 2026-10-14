"""Local WSGI server using the same Neon-aware factory as hosted deployments."""
import os
from socketserver import ThreadingMixIn
from wsgiref.simple_server import WSGIServer, make_server
from environment import load_environment


class ThreadedServer(ThreadingMixIn, WSGIServer):
    daemon_threads = True


if __name__ == '__main__':
    load_environment()
    from webapp import create_app
    host = os.environ.get('HOST', '127.0.0.1')
    port = int(os.environ.get('PORT', '8080'))
    app = create_app()
    with make_server(host, port, app, server_class=ThreadedServer) as server:
        print(f'MOVIS listening at http://{host}:{port}', flush=True)
        server.serve_forever()
