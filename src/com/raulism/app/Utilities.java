package com.raulism.app;

public class Utilities
{
	public static final int DIALOG_GETTING_USER = 100;
	public static final int DIALOG_GETTING_COLLECTION = 101;
	public static final int DIALOG_GETTING_HEAVY_ROTATION = 102;
	public static final int DIALOG_GETTING_ARTIST_RADIO = 103;
	public static final int DIALOG_GETTING_ARTIST_LIST = 104;
	
	public static int getProgressDialogMessage(int dialogId)
	{
		int messageId = 0;
		switch (dialogId)
		{
		case DIALOG_GETTING_USER:
			return R.string.getting_user;
		case DIALOG_GETTING_COLLECTION:
			return R.string.getting_collection;
		case DIALOG_GETTING_HEAVY_ROTATION:
			return R.string.getting_heavy_rotation;
		case DIALOG_GETTING_ARTIST_RADIO:
			return R.string.getting_artist_radio;
		case DIALOG_GETTING_ARTIST_LIST:
			return R.string.getting_artist_list;
		}
		return messageId;
	}
}
