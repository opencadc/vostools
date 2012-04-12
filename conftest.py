import pytest
from vos import Client

def pytest_funcarg__vossetup(request): 
    return VOSSetup(request)

def pytest_addoption(parser):
    import os
    parser.addoption("--certfile", 
                     action="store", 
                     default=os.path.join(os.getenv('HOME','.'),".ssl/cadcproxy.pem"),
                     help="proxy certificate to use in test")

class VOSSetup:
    def __init__(self, request):
        self.config = request.config

    def client(self):
        return Client(certFile=self.config.option.certfile)
