"""A utilities for dealing with CL signal handeling."""
import signal


def signal_handler(signum, frame):
    """
    signal handler for keyboard interupt of cl interface.

    :param signum: signal sent to CL tool.
    :param frame: frame where CL tool was running
    :raises KeyboardInterrupt
    """
    raise KeyboardInterrupt(
        "SIGNAL {0} from {1} signal handler".format(signum, frame))


signal.signal(signal.SIGINT, signal_handler)
