package es.amanzag.yatorrent.test;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;

import es.amanzag.yatorrent.metafile.MalformedMetadataException;
import es.amanzag.yatorrent.metafile.MetafileDownloader;
import es.amanzag.yatorrent.metafile.TorrentMetadata;

public class MetafileDownloadTest {

	/**
	 * @param args
	 * @throws IOException 
	 * @throws MalformedURLException 
	 * @throws MalformedMetadataException 
	 */
	public static void main(String[] args) throws MalformedURLException, IOException, MalformedMetadataException {
		System.out.println("downloading...");
		TorrentMetadata torrent = TorrentMetadata.createFromFile(MetafileDownloader.download(new URL("http://www.mininova.org/get/683454")));
		System.out.println(torrent.getInfoHash());
		System.out.println("end...");

	}

}
