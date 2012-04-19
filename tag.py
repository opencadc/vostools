#!python
"""set/read/(list) a property(ies) if a node.

The tag system is meant to allow tags, in addition to the standard
nodode properties. """


import time
import errno

def signal_handler(signal, frame):
    sys.stderr.write("Interupt\n")
    sys.exit(-1)



if __name__=='__main__':

    import argparse
    import logging, sys
    import vos, errno, os
    ## handle interupts nicely
    import signal
    signal.signal(signal.SIGINT, signal_handler)
    
    parser=argparse.ArgumentParser(description="List the tags on a file")

    parser.add_argument("--certfile",help="location of your CADC security certificate file",default=os.path.join(os.getenv("HOME","."),".ssl/cadcproxy.pem"))
                      
    parser.add_argument("node",action='store',help="The VOSpace node with the tag")
    parser.add_argument("key",action='store',help="The keyword for this tag",default=None,nargs='?')
    parser.add_argument("value",action='store',help="Value to assign",default=None,nargs='?')
                      
    opt=parser.parse_args()

    logLevel=logging.INFO
                      
    logging.basicConfig(level=logLevel,
                        format="%(asctime)s - %(module)s.%(funcName)s: %(message)s")

    
    try:
        client=vos.Client(certFile=opt.certfile)
    except Exception as e:
        logging.error("Conneciton failed:  %s" %  (str(e)))
        sys.exit(e.errno)

    import ssl

    node=client.getNode(opt.node)
    if opt.key is None:
        for key in node.props:
            if key not in ['date','MD5','type','length','ispublic','quota','creator','readgroup','writegroup']:
                print "%s -> %s" %(key,node.props[key])
    elif opt.value is None:
        print node.props[opt.key]
    else:
        node.changeProp(opt.key,opt.value)
        client.update(node)
        print client.getNode(opt.node).props[opt.key]
        
    sys.exit(0)
