package net.dxs.asynchttpclient;

import org.apache.http.Header;

import android.app.Activity;
import android.os.Bundle;
import android.widget.Toast;

import com.loopj.android.http.AsyncHttpClient;
import com.loopj.android.http.JsonHttpResponseHandler;
import com.loopj.android.http.TextHttpResponseHandler;

public class MainActivity extends Activity {

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		sendGetRequest();
	}

	private void sendGetRequest() {
		new AsyncHttpClient().get("http://www.baidu.com", new TextHttpResponseHandler() {

            @Override
            public void onSuccess(int statusCode, Header[] headers, String responseString) {
            }

            @Override
            public void onFailure(int statusCode, Header[] headers, String responseString, Throwable throwable) {
                Toast.makeText(getApplicationContext(), "服务器忙!!! " + statusCode, Toast.LENGTH_SHORT).show();                
            }
        });	
		
		AsyncHttpClient client = new AsyncHttpClient();
		client.get("https://www.baidu.com", new JsonHttpResponseHandler(){
		});
		
//		AsyncHttpClient client = new AsyncHttpClient();
//		 client.get("https://www.google.com", new AsyncHttpResponseHandler() {
//		     @Override
//		     public void onSuccess(int statusCode, Header[] headers, byte[] responseBody) {
//		          System.out.println(response);
//		     }
//		     @Override
//		     public void onFailure(int statusCode, Header[] headers, byte[] responseBody, Throwable
//		 error)
//		 {
//		          error.printStackTrace(System.out);
//		     }
//		 });

	}
}
