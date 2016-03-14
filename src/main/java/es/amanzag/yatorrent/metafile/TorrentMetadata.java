/**
 * 
 */
package es.amanzag.yatorrent.metafile;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import es.amanzag.yatorrent.bencoding.BDecoder;
import es.amanzag.yatorrent.bencoding.BEncoder;
import es.amanzag.yatorrent.bencoding.BEncodingException;

/**
 * @author Alberto Manzaneque
 *
 */
public class TorrentMetadata {
	
	public static class ContainedFile {
		private String name;
		private Integer length;
		private ContainedFile() {}
		public long getLength() {
			return length;
		}
		public String getName() {
			return name;
		}
	}
	
	
	private String announce;
	private List<String> announceList;
	private Date creationDate;
	private String createdBy;
	private String comment;
	private boolean multifile;
	private Integer pieceLength;
	private List<byte[]> pieceHashes;
	private String directory;
	private List<ContainedFile> files;
	private byte[] infoHash;
	private long totalLength;
	
	private TorrentMetadata() {
		pieceHashes = new ArrayList<byte[]>();
		files = new ArrayList<ContainedFile>();
	}
	
	
	public static TorrentMetadata createFromFile(File file) throws IOException, MalformedMetadataException {
		BDecoder decoder = new BDecoder(new FileInputStream(file));
		Map<String, Object> root = null;

		TorrentMetadata result = new TorrentMetadata();
		try {
		    root = decoder.decodeNext();
		    result.announce = (String) root.get("announce");
			if(result.announce == null) {
			    throw new MalformedMetadataException("announce not present in .torrent");
			}
			
			List<List<String>> announceList = (List<List<String>>) root.get("announce-list");
			if(announceList == null) {
			    result.announceList = Collections.emptyList();
			} else {
			    result.announceList = announceList.stream().map(al -> al.get(0)).collect(Collectors.toList());
			}
			
			
			Integer creationDate = (Integer) root.get("creation date");
			if(creationDate != null) {
			    result.creationDate = new Date(creationDate);
			}
			
			result.comment = (String) root.get("comment");
			result.createdBy = (String) root.get("created by");
			
			@SuppressWarnings("unchecked")
            Map<String, Object> info = (Map<String, Object>) root.get("info");
			if(info == null) {
                throw new MalformedMetadataException("announce not present in .torrent");
            }
			
			// obtain the info_hash
			ByteArrayOutputStream stream = new ByteArrayOutputStream(1024);
			BEncoder encoder = new BEncoder(stream);
			encoder.encode(info);
			MessageDigest sha1 = MessageDigest.getInstance("SHA1");
			byte[] data = Arrays.copyOf(stream.toByteArray(), stream.size());
			sha1.update(data);
			result.infoHash = sha1.digest();
			
			root = info;
			result.pieceLength = (Integer) root.get("piece length");
			if(result.pieceLength == null) {
                throw new MalformedMetadataException("piece length not present in .torrent");
            }
			
			String pieces = (String) root.get("pieces");
			if(pieces == null) {
                throw new MalformedMetadataException("pieces not present in .torrent");
            }
			byte[] hashes = BDecoder.toBytes(pieces);
			if(hashes.length % 20 != 0)
				throw new MalformedMetadataException("incorrect length of pieces");
			for(int bytesRead = 0; bytesRead < hashes.length; bytesRead += 20) {
				result.pieceHashes.add(Arrays.copyOfRange(hashes, bytesRead, bytesRead+20));
			}
			
			Integer length = (Integer) root.get("length");
			if(length != null) { // 1 file torrent
				result.directory = "";
				ContainedFile cFile = new ContainedFile();
				cFile.length = length;
				cFile.name = (String) root.get("name");
				if(cFile.name == null) {
                    throw new MalformedMetadataException("file name not present in .torrent");
                }
				result.files.add(cFile);
			} else if((root.get("files")) != null) { // multifile torrent
			    result.multifile = true;
			    result.directory = (String) root.get("name");
				if(result.directory == null) {
                    throw new MalformedMetadataException("file name not present in .torrent");
                }
				
				@SuppressWarnings("unchecked")
                List<Object> fileList = (List<Object>) root.get("files");
				if(fileList.size() == 0) {
                    throw new MalformedMetadataException(".torrent contains no files");
                }
				for (Object next : fileList) {
					@SuppressWarnings("unchecked")
                    Map<String, Object> dir =  (Map<String, Object>) next;
					ContainedFile cFile = new ContainedFile();
	
					cFile.length = (Integer) dir.get("length");
					if(cFile.length == null) {
                        throw new MalformedMetadataException("file length not present");
                    }
					
					@SuppressWarnings("unchecked")
                    List<String> pathList = (List<String>) dir.get("path");
					if(pathList == null) {
                        throw new MalformedMetadataException("file path not present");
                    }
					if(pathList.size() == 0)
						throw new MalformedMetadataException("file path not present");
					StringBuffer path = new StringBuffer();
					for (String pathElement : pathList) {
						path.append(pathElement);
						path.append(File.separator);
					}
					path.deleteCharAt(path.length()-1);
					cFile.name = path.toString();
	
					result.files.add(cFile);
				}
				
			} else {
				throw new MalformedMetadataException("no file found in .torrent");
			}
			
			result.totalLength = 0;
			for (ContainedFile tmpFile : result.files) {
				result.totalLength += tmpFile.getLength();
			}
			
		} catch (ClassCastException e) {
			throw new MalformedMetadataException("not valid structure", e);
		} catch (BEncodingException e) {
		    throw new MalformedMetadataException("File "+file.getAbsolutePath()+" is not a well-formed .torrent", e);
		} catch (NoSuchAlgorithmException e) {
			// TODO escribir un error entendible
			e.printStackTrace();
			System.exit(1);
		}
		
		return result;
	}


	public String getAnnounce() {
		return announce;
	}

    public List<String> getAnnounceList() {
        return announceList;
    }
	
	public String getComment() {
		return comment;
	}


	public String getCreatedBy() {
		return createdBy;
	}


	public Date getCreationDate() {
		return creationDate;
	}


	public String getDirectory() {
		return directory;
	}


	public List<ContainedFile> getFiles() {
		return files;
	}


	public boolean isMultifile() {
		return multifile;
	}


	public List<byte[]> getPieceHashes() {
		return pieceHashes;
	}


	public int getPieceLength() {
		return pieceLength;
	}
	
	public byte[] getInfoHash() {
		return infoHash;
	}
	
	public String getInfoHashHex() {
		return new BigInteger(infoHash).toString(16);
	}
	
	public String getName() {
		return directory.length() != 0 ? directory : files.get(0).name;
	}
	
	public long getTotalLength() {
		return totalLength;
	}
	
}
