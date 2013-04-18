package com.example.sdcardbrowse;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import android.os.Bundle;
import android.app.Activity;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Toast;

public class MainActivity extends Activity  {

	private static final String TAG =  "browse";
	private List<String> items = null;
	private static ListView m_listview; 
	private View mContentView = null;

	@Override
	public void onCreate(Bundle icicle) {
		super.onCreate(icicle);
		setContentView(R.layout.activity_main);		

		Button btn = (Button) findViewById(R.id.button1);

		OnClickListener listener = new OnClickListener() {

			@Override
			public void onClick(View v) {

				File[] files = new File("/").listFiles();
				items = new ArrayList<String>();
				for(File file : files){
					//if(file.toString().toLowerCase().endsWith("wav"))
						items.add(file.getPath());
				}				
				m_listview = (ListView) findViewById(R.id.listitems);

				ArrayAdapter<String> adapter = new ArrayAdapter<String>(getBaseContext(), R.layout.file_list_row, items);
				m_listview.setAdapter(adapter);

				OnItemClickListener listener1 = new OnItemClickListener() {

					@Override
					public void onItemClick(AdapterView<?> arg0, View v, int position, long id) {
						int selectedRow = (int)id;
						File file = new File(items.get(selectedRow));

						Toast.makeText(getBaseContext(), " File to stream : " 
								+ file.getName(), Toast.LENGTH_SHORT).show();
						Log.d(TAG, "Item selected : " + file.toString());
					}			
				};
				m_listview.setOnItemClickListener(listener1);
			}
		};

		btn.setOnClickListener(listener);  

	}

}
