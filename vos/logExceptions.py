import logging
import traceback


def logExceptions():
    """
    A decorator which catches and logs exceptions.
    """

    def decorator(func):
        def wrapper(*args, **kwds):
            try:
                return func(*args, **kwds)
            except Exception as e:
                logging.error("Exception throw: %s %s" % (type(e), str(e)))
                logging.error(traceback.format_exc())
                raise
        return wrapper
    return decorator
