/**
 * 
 */
package com.erakk.lnreader.dao;

import java.net.URL;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;

import org.jsoup.Jsoup;
import org.jsoup.Connection.Response;
import org.jsoup.nodes.Document;

import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;

import com.erakk.lnreader.Constants;
import com.erakk.lnreader.helper.AsyncTaskResult;
import com.erakk.lnreader.helper.DBHelper;
import com.erakk.lnreader.helper.DownloadFileTask;
import com.erakk.lnreader.helper.DownloadPageTask;
import com.erakk.lnreader.model.ImageModel;
import com.erakk.lnreader.model.NovelCollectionModel;
import com.erakk.lnreader.model.PageModel;
import com.erakk.lnreader.parser.BakaTsukiParser;


/**
 * @author Nandaka
 * 
 */
public class NovelsDao {
	private static final String TAG = NovelsDao.class.toString();
	
	private ArrayList<PageModel> list;
	private static DBHelper dbh;
	
	public NovelsDao(Context context) {
		if(dbh == null)
			dbh = new DBHelper(context);	
	}
	  
	public ArrayList<PageModel> getNovels() throws Exception{
		boolean refresh = false;
		//dbh.deleteDB();
		PageModel page = dbh.selectFirstBy(DBHelper.COLUMN_PAGE, "Main_Page");
		if(page == null) {
			Log.d(TAG, "No Main_Page data!");
			refresh = true;
		}
		else {
			// get last updated page revision from internet
			
			// compare if less than x day
		}
		
		//refresh = true; //debug only
		
		if(refresh){
			list = getNovelsFromInternet();
			page = new PageModel();
			page.setPage("Main_Page");
			page.setTitle("Main Page");
			page.setType(PageModel.TYPE_OTHER);
			page.setParent("");
			page.setLastUpdate(new Date());
			page.setLastCheck(new Date());
			try{
				dbh.insertOrUpdate(page);
			}catch(Exception e) {
				Log.e(TAG, "Failed to insert: " + page.toString() + ", " +e.getMessage());
			}
			Log.d(TAG, "Updated Main_Page");
			
			for(Iterator<PageModel> i = list.iterator(); i.hasNext();){
				PageModel p = i.next();
				try{
					dbh.insertOrUpdate(p);
				}catch(Exception e) {
					Log.e(TAG, "Failed to insert: " + p.toString());
					e.printStackTrace();
				}
			}			
		}
		else {
			list = dbh.selectAllByColumn(DBHelper.COLUMN_TYPE, PageModel.TYPE_NOVEL);
			Log.d(TAG, "Found: " + list.size());
		}
		return list;

	}
	
	public ArrayList<PageModel> getNovelsFromInternet() throws Exception {
		list = new ArrayList<PageModel>();

		Response response = Jsoup.connect(Constants.BaseURL)
				 .timeout(60000)
				 .execute();
		Document doc = response.parse();//result.getResult();
		
		list = BakaTsukiParser.ParseNovelList(doc);
		
		Log.d(TAG, "Found: "+list.size()+" Novels");
		return list;
	}

	public NovelCollectionModel getNovelDetails(PageModel page) throws Exception {
		NovelCollectionModel novel = dbh.getNovelDetails(page.getPage());
		
		if(novel == null) {
			novel = getNovelDetailsFromInternet(page);
		}
		
		return novel;
	}
	
	public NovelCollectionModel getNovelDetailsFromInternet(PageModel page) throws Exception {
		NovelCollectionModel novel = null;
		URL url = new URL(Constants.BaseURL + "index.php?title=" + page.getPage());

		AsyncTask<URL, Void, AsyncTaskResult<Document>> task = new DownloadPageTask().execute(new URL[] {url});
		AsyncTaskResult<Document> result = task.get();
		if(result.getError() != null) {
			result.getError().printStackTrace();
			throw result.getError();
		}
		Document doc = result.getResult();
		
		novel = BakaTsukiParser.ParseNovelDetails(doc, page);
		dbh.insertNovelDetails(novel);
		
		// download cover image
		if(novel.getCoverUrl() != null) {
			AsyncTask<URL, Integer, AsyncTaskResult<ImageModel>> download = new DownloadFileTask().execute(new URL[] {novel.getCoverUrl()});
			AsyncTaskResult<ImageModel> result2 = download.get();
			if(result2.getError() != null) {
				result2.getError().printStackTrace();
				throw result2.getError();
			}
			ImageModel image = result2.getResult();
			Log.d("Image", image.toString());
		}
		
		return novel;
	}
	
}
