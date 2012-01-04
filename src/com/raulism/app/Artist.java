package com.raulism.app;

import org.json.JSONException;
import org.json.JSONObject;

//http://developer.rdio.com/docs/read/rest/types#Artist

public class Artist
{
	private String _key;
	private String _name;

	public Artist(String key, String name)
	{
		_key = key;
		_name = name;
	}
	
	public String getKey()
	{
		return _key;
	}
	
	public String getName()
	{
		return _name;
	}
	
	@Override
	public String toString()
	{
		return _name;
	}
	
	public static Artist fromJson(JSONObject artistObject) throws JSONException
	{
		String key = artistObject.getString("key");
		String name = artistObject.getString("name");

		Artist artist = new Artist(key, name);
		return artist;
	}
}