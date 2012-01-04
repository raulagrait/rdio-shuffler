package com.raulism.app;

import org.json.JSONException;
import org.json.JSONObject;

// Our model for the metadata for a track that we care about
public class Track 
{
	private String _key;
	private String _trackName;
	private String _artistName;
	private String _albumName;
	private String _albumArt;

	public Track(String k, String name, String artist, String album, String uri)
	{
		_key = k;
		_trackName = name;
		_artistName = artist;
		_albumName = album;
		_albumArt = uri;
	}
	
	public String getKey()
	{
		return _key;
	}
	
	public String getTrackName()
	{
		return _trackName;
	}
	
	public String getAlbumName()
	{
		return _albumName;
	}
	
	public String getArtistName()
	{
		return _artistName;
	}
	
	public String getAlbumArt()
	{
		return _albumArt;
	}
	
	public static Track fromJson(JSONObject trackObject) throws JSONException
	{
		String key = trackObject.getString("key");
		String name = trackObject.getString("name");
		String artist = trackObject.getString("artist");
		String album = trackObject.getString("album");
		String albumArt = trackObject.getString("icon");
		
		Track track = new Track(key, name, artist, album, albumArt);
		return track;
	}
}