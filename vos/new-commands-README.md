## vostools 3.1.0

### Proposed command line (vcp, vrm) syntax 
Initially the new commands will only be supported by the minoc service.
#### vcp

    usage: vcp [-h] [--certfile CERTFILE] [--token TOKEN] [--version] [-d]
               [--vos-debug] [-v] [-w] [--exclude EXCLUDE] [--include INCLUDE]
               [-i] [--overwrite] [--quick] [-L] [--ignore] [--head]
               source [source ...] destination

Copy files from source to destination.  A resource ID is required to determine the service to be used. The resource ID will reside in a configuration file. Example resource ID for vospace is ivo://cadc.nrc.ca/vault. Example resource ID for minoc ivo://cadc.nrc.ca/???/minoc, where ??? has yet to be defined. There will be multiple resource IDs for minoc, one per storage stie. 

Positional Argument | Description        |
----------------------------|----------------------|
 Source                   | a URI or a local filename. URI will not support wild card, i.e. cadc:TEST/f\* will be invalid. Local filename will support wild card, e.g. *.fits is valid|
 Destination           | a URI or a local filename. URI will not support wild card, i.e. cadc:TEST/f\* will be invalid. Local filename will support wild card, e.g. *.fits is valid|

A URI is differentiated from a filename by '<scheme\>:'. When a URI is used to specify the destination, if the URI specifies a directory, it must end with a '/'. 

Optional Arguments | Description     |
----------------------------|------------------|
-h, --help | show the help message and exit|
--certfile CERTFILE | filename of your CADC X509 authentication certificate|
--token TOKEN | authentication token string (alternative to certfile|
--version | show program's version number and exit |
-d, --debug| print on command debug messages.|
--vos-debug| print on vos debug messages.|
-v, --verbose | print verbose messges|
-w, --warning | print warning messages only |
--exclude EXCLUDE | skip files that match pattern (overrides include) |
--include INCLUDE | only copy files that match pattern |
-i, --interrogate | ask before overwriting files |
--quick | assuming CANFAR VOSpace, only compatible with CANFAR VOSpace.|
-L, --follow-links | follow symbolic links. Default is to not follow links. |
--ignore | ignore errors and continue with recursive copy |
--head | copy only the headers of a file from vospace. Might return an error if the server does not support the operation ona given file type. |

Examples:
To reduce clutter, optional arguments are not included in the examples.

Put a local file foo.fits into cadc:TEST. Please note that the '.' at the end will be required

    vcp foot.fits cadc:TEST/

Put all local files with '.fits' suffix into cadc:TEST

    vcp *.fits cadc:TEST/
    
Put a local file foo.fits into cadc:TEST as foobar.fits

    vcp foo.fits cadc:TEST/foobar.fits
    
Get a file foo.fits from cadc:TEST into the current directory

    vcp cadc:TEST/foo.fits .
    
Get a file foobar.fits from cadc:TEST into the current directory as foo.fits

    vcp cadc:TEST/foobar.fits foo.fits
    
Get a file foo.fits from cadc:TEST into the /tmp directory as foo.fits

    vcp cadc:TEST/foo.fits /tmp
    
#### vrm

    usage: vrm [-h] [--certfile CERTFILE] [--token TOKEN] [--version] [-d]
           [--vos-debug] [-v] [-w]
           uri [uri ...]

Remove one or more files identified by a URI. Wild card will not be supported, e.g. cadc:TEST/*.fits will be invalid.

Positional Argument | Description        |
----------------------------|----------------------|
 uri    | a URI identifying the file to be removed |
 
 Optional Arguments | Description     |
----------------------------|------------------|
-h, --help | show the help message and exit|
--certfile CERTFILE | filename of your CADC X509 authentication certificate|
--token TOKEN | authentication token string (alternative to certfile|
--version | show program's version number and exit |
-d, --debug| print on command debug messages.|
--vos-debug| print on vos debug messages.|
-v, --verbose | print verbose messges|
-w, --warning | print warning messages only |

Examples:
To reduce clutter, optional arguments are not included in the examples.

Remove file foo.fits from cadc:TEST. 

    vrm cadc:TEST/foo.fits
    
Remove files foo.fits, foobar.fits, goobar.fits from cadc:TEST

    vrm cadc:TEST/foo.fits cadc:TEST/foobar.fits cadc:TEST/goobar.fits 
    
