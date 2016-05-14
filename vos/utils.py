__author__ = 'jjk'

import errno
import os


def mkdir_p(path, mode):
    try:
        os.makedirs(path, mode)
    except OSError as exc:  # Python >2.5
        if exc.errno == errno.EEXIST and os.path.isdir(path):
            os.chmod(path, mode)
        elif exc.errno == errno.EACCES:
            raise OSError(errno.EACCES, "permission denied to create {0}".format(path))
        else:
            raise OSError(errno.ENOTDIR, "{0} exists and is not a directory".format(path))
