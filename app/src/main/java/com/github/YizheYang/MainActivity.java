/*
 * Copyright <2021> WISStudio Inc.
 */
package com.github.YizheYang;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.provider.MediaStore;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import static java.lang.Thread.sleep;

/**
 * 主活动类
 * @author 一只羊
 */
public class MainActivity extends AppCompatActivity {

	protected int spanCount = 3;
	protected int firstNum = spanCount * 5;
	protected int moreNum = spanCount * 2;
	protected int allNum = firstNum;
	protected boolean isNew = true;
	private final String path = "http://shibe.online/api/shibes?count=1&urls=true&httpsUrls=true";
	private SwipeRefreshLayout swipe;
	private RecyclerView recyclerView;
	private OverScrollLayout Layout;
	private ProgressBar pgb_top;
	private ProgressBar pgb_bottom;
	private ScrollView scrollView;
	private ImageView img;
	private ImageAdapter adapter;
	public ImageView largeImage;
	private ImageView largeImage2;
	private Dialog dialog;
	private AlertDialog.Builder builder;
	private final List<Image> imageList = new ArrayList<>();
	private final List<Image> blankList = new ArrayList<>();
	private int i = 0;
	private Image tempImage;
	private MySQLiteOpenHelper myHelper;

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(@NonNull MenuItem item) {
		switch (item.getItemId()) {
			case R.id.test:
				Intent intent = new Intent(MainActivity.this ,SecondActivity.class);
				startActivity(intent);
				break;
			default:
		}
		return true;
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		//隐藏标题栏
//		if(getSupportActionBar() != null){
//			getSupportActionBar().hide();
//		}
		pgb_top = findViewById(R.id.progressbar_top);
		pgb_bottom = findViewById(R.id.progressbar_bottom);
		recyclerView = findViewById(R.id.recyclerView);
		swipe = findViewById(R.id.swipe);
//		largeImage = (ImageView)findViewById(R.id.largeImageTest);
		Layout = findViewById(R.id.layout);
		myHelper = new MySQLiteOpenHelper(MainActivity.this, "Image.db", null, 1);
		myHelper.getWritableDatabase();//创建数据库，如果存在则不操作

		recyclerView.setLayoutManager(new GridLayoutManager(MainActivity.this, spanCount));
		adapter = new ImageAdapter(imageList, MainActivity.this);
		recyclerView.setAdapter(adapter);
		//自定义单击事件，当图片被单击时显示原图
		adapter.setOnItemClickListener((view, position) -> {
			Message large = new Message();
			large.what = 3;
			Object[] data = new Object[2];
			data[0] = imageList.get(position).getImage();
			data[1] = position;
			large.obj = data;
			displayLarge.sendMessage(large);
		});
		initImage();//加载图片
		setEnlarge();//设置放大显示格式

		swipe.setOnRefreshListener(() -> {
			//设置下拉刷新
			pgb_top.setVisibility(View.VISIBLE);
			imageList.clear();
			adapter.notifyDataSetChanged();
			allNum = firstNum;
			isNew = true;
			initImage();
			swipe.setRefreshing(false);
		});

		Layout.setScrollListener(() -> {
			//设置上拉加载
			if (!Layout.canPullUp()) {
//					if (isConnect()) {
//						allNum += moreNum;
//						recyclerView.setNestedScrollingEnabled(false);
//						Toast.makeText(MainActivity.this,"正在刷新中，请不要滑动...",Toast.LENGTH_LONG).show();
//						initPicture();
//					}
			}
		});

		recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
			//设置上拉加载
			@Override
			public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
				super.onScrollStateChanged(recyclerView, newState);
				if (!recyclerView.canScrollVertically(-1)) {
					if (isConnect()) {
						allNum += moreNum;
						Toast.makeText(MainActivity.this, "正在刷新中，请不要滑动...", Toast.LENGTH_LONG).show();
//						Message message = new Message();
//						message.what = 4;
//						blank.sendMessage(message);
						initImage();
					}
				}
			}
		});
	}

	/**
	 * 将图片数据添加进数组，并且设置尾部
	 * 当加载完毕后更新UI
	 */
	@SuppressLint("HandlerLeak")
	private final Handler addImage = new Handler(Looper.getMainLooper()) {
		@SuppressLint("InflateParams")
		@Override
		public void handleMessage(Message msg) {
			super.handleMessage(msg);
			tempImage = new Image("0", null);
			if (isConnect() && adapter.VIEW_FOOTER == null) {
				adapter.addFooterView(LayoutInflater.from(MainActivity.this).inflate(R.layout.refresh_bottom, null));
			} else if (!isConnect() && adapter.VIEW_FOOTER == null) {
				adapter.addFooterView(LayoutInflater.from(MainActivity.this).inflate(R.layout.local_display, null));
			}
			if (msg.what == 1) {
				Message update;
				Object[] obj = (Object[]) msg.obj;
				Bitmap bm = (Bitmap) obj[0];
				String message = (String) obj[1];
				tempImage = new Image(message, bm);
				imageList.add(tempImage);
				if (((imageList.size() == allNum) && isNew) || !isConnect()) {
					pgb_top.setVisibility(View.GONE);
					adapter.notifyDataSetChanged();
					isNew = false;
//					update = new Message();
//					update.what = 1;
//					updateView.sendMessage(update);
					Toast.makeText(MainActivity.this, "获取到" + firstNum + "张图片", Toast.LENGTH_SHORT).show();
				} else if (imageList.size() == allNum) {
					pgb_top.setVisibility(View.GONE);
					adapter.notifyDataSetChanged();
//					update = new Message();
//					update.what = 1;
//					updateView.sendMessage(update);
					Toast.makeText(MainActivity.this, "刷新成功", Toast.LENGTH_SHORT).show();
				}
			} else {
				Toast.makeText(MainActivity.this, "ERROR", Toast.LENGTH_SHORT).show();
			}
		}
	};

	@SuppressLint("HandlerLeak")
	private final Handler updateView = new Handler(Looper.getMainLooper()) {
		@SuppressLint("InflateParams")
		@Override
		public void handleMessage(@NonNull Message msg) {
			super.handleMessage(msg);
			if (msg.what == 1) {
//				recyclerView.layout(recyclerView.getLeft(), recyclerView.getTop(), recyclerView.getRight(),
//					recyclerView.getBottom() + adapter.VIEW_FOOTER.getMeasuredHeight());
				adapter.notifyDataSetChanged();
				Toast.makeText(MainActivity.this, "刷新成功", Toast.LENGTH_SHORT).show();
				recyclerView.setNestedScrollingEnabled(true);
			} else if (msg.what == 2) {
				if (isConnect()) {
					adapter.addFooterView(LayoutInflater.from(MainActivity.this).inflate(R.layout.refresh_bottom, null));
					Toast.makeText(MainActivity.this, "获取到" + firstNum + "张图片", Toast.LENGTH_SHORT).show();
				} else {
					adapter.addFooterView(LayoutInflater.from(MainActivity.this).inflate(R.layout.local_display, null));
				}
			}
		}
	};

	/**
	 * 更新放大显示的UI
	 */
	@SuppressLint("HandlerLeak")
	private final Handler displayLarge = new Handler(Looper.getMainLooper()) {
		@Override
		public void handleMessage(@NonNull Message msg) {
			super.handleMessage(msg);
			if (msg.what == 3) {
				Object[] data = (Object[]) msg.obj;
				Bitmap bm = (Bitmap) data[0];
				largeImage2.setImageBitmap(bm);
				largeImage2.setTag(data[1]);
				largeImage2.setVisibility(View.VISIBLE);
//				dialog.show();
				builder.create();
				builder.show();
			}
		}
	};

	@SuppressLint("HandlerLeak")
	private final Handler addBlank = new Handler(Looper.getMainLooper()) {
		@Override
		public void handleMessage(@NonNull Message msg) {
			super.handleMessage(msg);
			if (msg.what == 4) {
				Bitmap bm = BitmapFactory.decodeResource(getResources(), R.drawable.test);
				for (i = 0; i < allNum; i++) {
					tempImage = new Image("0", bm);
					blankList.add(tempImage);
				}
				recyclerView.setLayoutManager(new GridLayoutManager(MainActivity.this, spanCount));
				ImageAdapter blankAdapter = new ImageAdapter(blankList, MainActivity.this);
				recyclerView.setAdapter(blankAdapter);
			}
		}
	};

	private void setEnlarge() {
		//用alertdialog显示原图，并且设置保存按钮和退出按钮
		new Thread(() -> {
			@SuppressLint("InflateParams")
			View view = LayoutInflater.from(MainActivity.this).inflate(R.layout.large_image, null);
			LinearLayout linearLayout = view.findViewById(R.id.largeImageLayout);
			largeImage2 = view.findViewById(R.id.largeImage2);
			builder = new AlertDialog.Builder(MainActivity.this, R.style.edit_AlertDialog_style).setView(linearLayout);
			builder.setPositiveButton("保存", (dialog, which) -> {
				saveBitmap(imageList.get((int) largeImage2.getTag()).getImage(),
						imageList.get((int) largeImage2.getTag()).getMessage());
				((ViewGroup) view.getParent()).removeView(view);
				dialog.dismiss();
			});
			builder.setNegativeButton("取消", (dialog, which) -> {
				((ViewGroup) view.getParent()).removeView(view);
				dialog.dismiss();
			});
			//		dialog = new Dialog(MainActivity.this, R.style.edit_AlertDialog_style);
			//		dialog.setContentView(R.layout.activity_main);
			//		dialog.setCanceledOnTouchOutside(true);
			//		largeImage = (ImageView)dialog.findViewById(R.id.largeImageTest);
			//		largeImage.setOnClickListener(new View.OnClickListener() {
			//			@Override
			//			public void onClick(View v) {
			//				dialog.dismiss();
			//			}
			//		});
			//		largeImage.setOnLongClickListener(new View.OnLongClickListener() {
			//			@Override
			//			public boolean onLongClick(View v) {
			//				saveBitmap(pictureList.get((int)largeImage.getTag()).getImage(), pictureList.get((int)largeImage.getTag()).getMessage());
			//				return true;
			//			}
			//		});
		}).start();
	}

	private void saveBitmap(Bitmap bitmap, String message) {
		//将图片保存到相册并且将相关信息存到数据库
		String name, result;
		Intent it;
		SQLiteDatabase db;
		ContentValues cv;
		requestPower();//check permission
		name = message.substring(message.indexOf("/") + 8, message.indexOf("."));
		result = MediaStore.Images.Media.insertImage(getContentResolver(), bitmap, name, message);
		it = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.parse(result));
		sendBroadcast(it);
		Toast.makeText(MainActivity.this, "保存成功!", Toast.LENGTH_SHORT).show();
		cv = new ContentValues();
		cv.put("MESSAGE", message);
		cv.put("LOCATION", Environment.getExternalStorageDirectory() + "/Pictures/" + name + ".jpg");
		cv.put("URL", "https://cdn.shibe.online/shibes/" + name + ".jpg");
		db = myHelper.getWritableDatabase();
		db.insert("SavedImage", null, cv);
		db.close();
		cv.clear();
		//		//创建文件，因为不存在2级目录，所以不用判断exist，要保存png，这里后缀就是png，要保存jpg，后缀就用jpg
		//		String path = "/" + name + ".jpg";
		//		Log.d(TAG, "saveBitmap: " + Environment.getExternalStorageDirectory() + getPackageName());
		////		File dir = new File(Environment.getExternalStorageDirectory() + "/" + getPackageName());
		////		File file = new File(dir, path);
		//		File file = new File( "data/data/com.github.YizheYang" + path);
		//		try {
		//			Log.d(TAG, "exist " + file.exists());
		//			if (!file.exists()){
		//				//file.mkdirs();
		//				file.createNewFile();
		//			}
		//			FileOutputStream fileOutputStream = new FileOutputStream(file);
		//			//压缩图片，如果要保存png，就用Bitmap.CompressFormat.PNG，要保存jpg就用Bitmap.CompressFormat.JPEG,质量是100%，表示不压缩
		//			bitmap.compress(Bitmap.CompressFormat.JPEG, 100, fileOutputStream);
		//			fileOutputStream.flush();
		//			fileOutputStream.close();
		//			Toast.makeText(MainActivity.this, "保存成功!", Toast.LENGTH_SHORT).show();
		//		} catch (IOException e) {
		//			e.printStackTrace();
		//		} finally {
		//			bitmap.recycle();
		//		}
		//		Intent intent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
		//		Uri uri = Uri.fromFile(file);
		//		intent.setData(uri);
		//		getApplicationContext().sendBroadcast(intent);
	}

	public void requestPower() {
		//请求所需的权限
		if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
			//refuse == true
			if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
				Toast.makeText(MainActivity.this, "请同意授权以保证程序正常运行", Toast.LENGTH_SHORT).show();
			} else {
				ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE,
						Manifest.permission.MOUNT_UNMOUNT_FILESYSTEMS}, 1);
			}
		}
	}

	/**
	 * 判断设备是否联网
	 * @return true是已联网，false是未联网
	 */
	private boolean isConnect() {
		ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
		NetworkInfo ni = cm.getActiveNetworkInfo();
		return ni != null && ni.isAvailable();
	}

	private void initImage() {
		//在联网的情况下从网络获取图片，未联网的情况下从本地加载
		new Thread(() -> {
			if (isConnect()) {
				if (isNew) {
					for (i = 0; i < firstNum; i++) {
						getDataFromInternet();
					}
				} else {
					for (i = 0; i < moreNum; i++) {
						getDataFromInternet();
					}
				}
			} else {
				String msg, lct;
				Bitmap bm;
				Object[] obj;
				Message img;
				SQLiteDatabase db = myHelper.getWritableDatabase();
				Cursor cs = db.query("SavedImage", null, null, null,
						null, null, null);
				if (cs.moveToFirst()) {
					do {
						msg = cs.getString(cs.getColumnIndex("MESSAGE"));
						lct = cs.getString(cs.getColumnIndex("LOCATION"));
						bm = BitmapFactory.decodeFile(lct);
						obj = new Object[2];
						obj[0] = bm;
						obj[1] = msg;
						img = new Message();
						img.what = 1;
						img.obj = obj;
						addImage.sendMessage(img);
					} while (cs.moveToNext());
				} else {
					Toast.makeText(MainActivity.this, "无本地图片", Toast.LENGTH_SHORT).show();
				}
				cs.close();
			}
		}).start();
	}

	/**
	 * 获取随机图片的地址
	 * @param url 是请求随机图片的命令
	 * @return 请求到的随机图片的地址
	 */
	private String getExactUrl(String url) {
		String line, result = null;
		URL u;
		HttpURLConnection huc;
		InputStream in = null;
		BufferedReader br = null;
		StringBuilder sb;
		try {
			u = new URL(url);
			huc = (HttpURLConnection) u.openConnection();
			huc.setRequestMethod("GET");
			huc.setConnectTimeout(50 * 1000);
			huc.setReadTimeout(80 * 1000);
			in = huc.getInputStream();
			br = new BufferedReader(new InputStreamReader(in));
			sb = new StringBuilder();
			while ((line = br.readLine()) != null) {
				sb.append(line);
			}
			result = sb.toString();
			result = result.substring(result.indexOf("[") + 2, result.indexOf("]") - 1);//cut
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			if (in != null) {
				try {
					in.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
			if (br != null) {
				try {
					br.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
		return result;
	}

	private void getDataFromInternet() {
		//从确定的网络地址获取图片
		new Thread(() -> {
			URL url;
			HttpURLConnection connection;
			InputStream is = null;
			Bitmap bm;
			Message img;
			try {
				url = new URL(getExactUrl(path));
				connection = (HttpURLConnection) url.openConnection();
				connection.setRequestMethod("GET");
				connection.setConnectTimeout(50 * 1000);
				connection.setReadTimeout(80 * 1000);
				if (connection.getResponseCode() == 200) {
					is = connection.getInputStream();
					bm = BitmapFactory.decodeStream(is);
					Object[] obj = new Object[2];
					obj[0] = bm;
					obj[1] = url.getPath();
					img = new Message();
					img.what = 1;
					img.obj = obj;
				} else {
					img = new Message();
					img.what = 0;
				}
				addImage.sendMessage(img);
//				sleep(1000);
			} catch (Exception e) {
				e.printStackTrace();
			} finally {
				if (is != null) {
					try {
						is.close();
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
			}
		}).start();
	}
}
