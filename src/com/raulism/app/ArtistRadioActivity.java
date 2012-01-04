package com.raulism.app;

import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;
import org.json.JSONArray;
import org.json.JSONObject;

import com.rdio.android.api.Rdio;
import com.rdio.android.api.RdioApiCallback;

import android.app.Activity;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.ListView;

public class ArtistRadioActivity extends Activity implements OnItemClickListener
{
	private ListView _artistListView;
	
	@Override
    public void onCreate(Bundle savedInstanceState)
	{
        super.onCreate(savedInstanceState);
        setContentView(R.layout.artist_radio_activity);
        
        initializeArtistListView();
        updateArtistList();
	}
	
	private void initializeArtistListView()
	{
		_artistListView = (ListView) findViewById(R.id.artistList);
		_artistListView.setOnItemClickListener(this);
		
		List<Artist> artistList = RdioShufflerApp.getInstance().getArtistList();
		updateArtistListView(artistList);
	}
	
	private void updateArtistList()
	{
		List<NameValuePair> args = new LinkedList<NameValuePair>();
		
		List<Artist> artistList = RdioShufflerApp.getInstance().getArtistList();
		if (artistList == null || artistList.size() == 0)
		{
			showDialog(Utilities.DIALOG_GETTING_ARTIST_LIST);
		}
		
		getRdio().apiCall("getArtistsInCollection", args, new RdioApiCallback()
		{
			@Override
			public void onApiSuccess(JSONObject result)
			{
				try
				{
					List<Artist> existingList = RdioShufflerApp.getInstance().getArtistList();
					if (existingList == null || existingList.size() == 0)
					{
						dismissDialog(Utilities.DIALOG_GETTING_ARTIST_LIST);
					}
					
					List<Artist> artistList = new LinkedList<Artist>();
					JSONArray artists = result.getJSONArray("result");
					for (int i = 0; i < artists.length(); i++)
					{
						JSONObject artistObject = artists.getJSONObject(i);
						Artist artist = Artist.fromJson(artistObject);

						Log.d(RdioShufflerActivity.TAG, "Found artist: " + artist.getKey() + " => " + artist.getName());
						artistList.add(artist);
					}

					updateArtistListView(artistList);
					RdioShufflerApp.getInstance().setArtistList(artistList);
				}				
				catch (Exception e)
				{				
					Log.e(RdioShufflerActivity.TAG, "Error getting artists in collection", e);
				}
			}
			
			@Override
			public void onApiFailure(String methodName, Exception e)
			{
				List<Artist> existingList = RdioShufflerApp.getInstance().getArtistList();
				if (existingList == null || existingList.size() == 0)
				{
					dismissDialog(Utilities.DIALOG_GETTING_ARTIST_LIST);
				}
				Log.e(RdioShufflerActivity.TAG, methodName + " failed: ", e);
			}
		});	
	}
	
	private void updateArtistListView(List<Artist> artistList)
	{
		if (artistList != null && artistList.size() > 0)
		{
			ArrayAdapter<Artist> adapter = new ArrayAdapter<Artist>(this, R.layout.artist_list_item, R.id.artistListItemName, artistList);
			_artistListView.setAdapter(adapter);
		}
	}
	
	private Rdio getRdio()
	{
		return RdioShufflerApp.getInstance().getRdio();
	}

	@Override
	public void onItemClick(AdapterView<?> adapter, View v, int position, long id)
	{
		Artist artist = (Artist) adapter.getItemAtPosition(position);
		
		List<NameValuePair> args = new LinkedList<NameValuePair>();
		args.add(new BasicNameValuePair("artist", artist.getKey()));
		args.add(new BasicNameValuePair("count", "1000"));
		
		showDialog(Utilities.DIALOG_GETTING_ARTIST_RADIO);
		
		getRdio().apiCall("getTracksForArtist", args, new RdioApiCallback()
		{
			@Override
			public void onApiSuccess(JSONObject result)
			{
				dismissDialog(Utilities.DIALOG_GETTING_ARTIST_RADIO);
				onGetTracksForArtistSuccess(result);
			}
			
			@Override
			public void onApiFailure(String methodName, Exception e)
			{
				dismissDialog(Utilities.DIALOG_GETTING_ARTIST_RADIO);
				Log.e(RdioShufflerActivity.TAG, methodName + " failed: ", e);
			}
		});
	}
	
	private void onGetTracksForArtistSuccess(JSONObject result)
	{
		try 
		{			
			List<Track> trackKeys = new LinkedList<Track>();
			JSONArray tracks = result.getJSONArray("result");
			int tracksToAdd = 100;
			for (int i = 0; i < tracksToAdd; i++)
			{
				int trackNum = (int) (Math.random() * tracks.length());
				
				JSONObject trackObject = tracks.getJSONObject(trackNum);				
				boolean canStream = trackObject.getBoolean("canStream");

				if (canStream)
				{
					Track track = Track.fromJson(trackObject);
					trackKeys.add(track);
					Log.d(RdioShufflerActivity.TAG, "Found track: " + track.getKey() + " => " + track.getTrackName());
				}
			}
			if (trackKeys.size() > 1)
			{
				getTrackQueue().clear();
				getTrackQueue().addAll(trackKeys);
				
				setResult(RESULT_OK);
				finish();
			}			
		}
		catch (Exception e)
		{
			Log.e(RdioShufflerActivity.TAG, "Failed to handle JSONObject: ", e);
		}
	}
	
	private Queue<Track> getTrackQueue()
	{
		return RdioShufflerApp.getInstance().getTrackQueue();
	}

	@Override
	protected Dialog onCreateDialog(int id)
	{
		int messageId = Utilities.getProgressDialogMessage(id);
		if (messageId != 0)
		{
			return ProgressDialog.show(this, "", getResources().getString(messageId));
		}
		return null;
	}
}
