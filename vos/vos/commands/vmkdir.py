#!python
"""Create a directory (ContainerNode) in the VOSpace repositotry"""


import os, sys, logging
from vos.commonparser import CommonParser
from vos import vos, version

def vmkdir():
    usage="""
            vmkdir vos:/root/node   -- creates a new directory (ContainerNode) called node at vospace root
     Version: %s """ % (version.version)

    parser=CommonParser(usage)
    parser.add_option("-p",action="store_true",help="Create intermediate directories as required.")

    if len(sys.argv) == 1:
            parser.print_help()
            sys.exit()

    (opt,args)=parser.parse_args()
    parser.process_informational_options()


    if len(args)>1:
        parser.error("Only one directory can be built per call")

    logging.info("Creating ContainerNode (directory) %s" % ( args[0]))

    try:

        client=vos.Client(vospace_certfile=opt.certfile,vospace_token=opt.token)

        dirNames=[]
        thisDir = args[0]
        if opt.p:
            while not client.access(thisDir):
                dirNames.append(os.path.basename(thisDir))
                thisDir = os.path.dirname(thisDir)
            while len(dirNames) > 0:
                thisDir = os.path.join(thisDir,dirNames.pop())
                client.mkdir(thisDir)
        else:
            client.mkdir(thisDir)

    except Exception as e:
        logging.error(str(e))
        sys.exit(getattr(e, 'errno', -1))


    sys.exit(0)
