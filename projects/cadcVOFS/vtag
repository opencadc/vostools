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
    parser.add_argument("key_value",action='store',help="The keyword for this tag",default=None,nargs='*',metavar="key [value]")
    #parser.add_argument("value",action='store',help="Value to assign",default=None,nargs='*')
                      
    opt=parser.parse_args()



    if len(opt.key_value) % 2 != 0 and len(opt.key_value) > 1:
        parser.print_help()
        sys.stderr.write("\n\nRequire either just one key to look-up or a set of  key/value pairs\n\n")
        sys.exit(-1)


    logLevel=logging.INFO
    logging.basicConfig(level=logLevel,
                        format="%(asctime)s - %(module)s.%(funcName)s: %(message)s")


    while True:
    
      try:
        client=vos.Client(certFile=opt.certfile)
        node=client.getNode(opt.node)

        if len(opt.key_value) == 0:
            for key in node.props:
                if key not in ['date','MD5','type','length','ispublic','quota','creator','readgroup','writegroup']:
                    print "%s -> %s" %(key,node.props[key])
        elif len(opt.key_value) == 1:
            print node.props.get(opt.key_value.pop(),"")
        else:
            ### Delete all properties that have name 'key'
            for i in range(0,len(opt.key_value),2):
                key=opt.key_value[i]
                if key in node.props:
                    changed=node.changeProp(key,None)
            client.addProps(node)
            node=client.getNode(opt.node)
            logging.debug(str(node))
            ### Insert all properties
            for i in range(0,len(opt.key_value),2):
                node.props[opt.key_value[i]]=opt.key_value[i+1]
            logging.debug("Calling AddPROPS with "+str(node))
            client.addProps(node)
        break
      except Exception as e:
        sys.stderr.write(str(e))
        sys.exit(-1)
    sys.exit(0)
