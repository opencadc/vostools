package ca.nrc.cadc.tap.test;

import java.io.File;
import java.net.URL;

import ca.nrc.cadc.tap.FileStore;

public class TestFileStore implements FileStore {

	@Override
	public URL put(File file) {
        throw new UnsupportedOperationException( "No file store implemented at this time." );
	}

}
