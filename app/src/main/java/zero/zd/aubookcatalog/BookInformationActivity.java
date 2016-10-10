package zero.zd.aubookcatalog;

import android.app.Dialog;
import android.app.DownloadManager;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.nostra13.universalimageloader.core.ImageLoader;
import com.nostra13.universalimageloader.core.assist.FailReason;
import com.nostra13.universalimageloader.core.listener.ImageLoadingListener;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;

import zero.zd.aubookcatalog.model.BookModel;

public class BookInformationActivity extends AppCompatActivity {

    private SharedPreferences preferences;
    private long bookId;
    private String bookType;
    private String studentId;

    private boolean isFavorite;
    BookModel bookModel;

    DownloadManager downloadManager;
    private long pdfDownload;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_book_information);
        // add up
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        // load server string
        preferences = getSharedPreferences(ZHelper.PREFS, MODE_PRIVATE);
        // load studId
        studentId = preferences.getString("student_id", null);
        Bundle extras = getIntent().getExtras();
        bookId = extras.getLong("bookId");
        bookType = extras.getString("bookType");

        // load book info
        new BookInformationTask().execute(bookId);

        registerReceiver(downloadReceiver,
                new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                finish();
                return true;

            case R.id.action_refresh:
                new BookInformationTask().execute(bookId);
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.activity_book_info, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(downloadReceiver);
    }

    public void onClickFavorite (View view) {
        new FavoriteTask().execute();
    }

    public void onClickDownload(View view) {
        Uri pdfUri = Uri.parse(bookModel.getPdf());
        pdfDownload = DownloadData(pdfUri);
    }

    private void setFavoriteImage() {
        ImageView imgView = (ImageView) findViewById(R.id.imgStar);

        if (isFavorite)
            imgView.setImageResource(R.drawable.ic_star_1);
        else
            imgView.setImageResource(R.drawable.ic_star_0);

    }


    private long DownloadData(Uri uri) {
        long downloadReference;
        downloadManager = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
        DownloadManager.Request request = new DownloadManager.Request(uri);
        request.setTitle("PDF Download");
        request.setDescription("Downloading " + bookModel.getBookTitle());

        request.setDestinationInExternalFilesDir(BookInformationActivity.this, Environment.DIRECTORY_DOWNLOADS, bookModel.getBookTitle() + ".pdf");

        downloadReference = downloadManager.enqueue(request);

        return downloadReference;
    }

    private BroadcastReceiver downloadReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            long referenceId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
            if (referenceId == pdfDownload) {
                Toast.makeText(BookInformationActivity.this, "PDF Downloaded", Toast.LENGTH_SHORT).show();

                // invoke pdf reader
                File file = ZHelper.getInstance().getPdf();
                File pdf = new File(file.getAbsolutePath() + "/" + bookModel.getBookTitle() + ".pdf");
                //Log.i("NFO", "PATH: " + file);
                Intent i = new Intent(Intent.ACTION_VIEW);
                i.setDataAndType(Uri.fromFile(pdf), "application/pdf");
                i.setFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);
                startActivity(i);
            }
        }
    };

    class BookInformationTask extends AsyncTask<Long, String, BookModel> {

//        Dialog mLoadingDialog;
        boolean isBook;

        @Override
        protected void onPreExecute() {
//            mLoadingDialog = ProgressDialog.show(getApplicationContext(), "Please wait", "Loading...");
            super.onPreExecute();
        }

        @Override
        protected BookModel doInBackground(Long... params) {
            String getInfo = "http://" + preferences.getString("serverIp", ZHelper.SERVER_IP)
                    + "/aubookcatalog/";

            if(bookType.equals("Book")) {
                getInfo += "getbookinfo.php";
                isBook = true;
            }
            else {
                getInfo += "getbookinfopdf.php";
                isBook = false;
            }

            try {
                long bookId = params[0];

                URL url = new URL(getInfo);
                HttpURLConnection httpURLConnection = (HttpURLConnection) url.openConnection();
                httpURLConnection.setConnectTimeout(3000);
                httpURLConnection.setReadTimeout(3000);

                httpURLConnection.setRequestMethod("POST");
                httpURLConnection.setDoInput(true);
                httpURLConnection.setDoOutput(true);

                OutputStream outputStream = httpURLConnection.getOutputStream();
                BufferedWriter bufferedWriter = new BufferedWriter(
                        new OutputStreamWriter(outputStream, ZHelper.DB_ENCODE_TYPE));

                String postData =
                        URLEncoder.encode("bookId", ZHelper.DB_ENCODE_TYPE) + "=" +
                                URLEncoder.encode(bookId + "", ZHelper.DB_ENCODE_TYPE);

                bufferedWriter.write(postData);
                bufferedWriter.flush();
                bufferedWriter.close();
                outputStream.close();

                InputStream inputStream = httpURLConnection.getInputStream();
                BufferedReader bufferedReader = new BufferedReader(
                        new InputStreamReader(inputStream, ZHelper.DB_ENCODE_TYPE));

                StringBuilder builder = new StringBuilder();
                String line;
                while ((line = bufferedReader.readLine()) != null) {
                    builder.append(line);
                }

                String JsonResult = builder.toString();
                JSONObject jsonObject = new JSONObject(JsonResult);
                JSONArray jsonArray = jsonObject.getJSONArray("result");


                JSONObject finalObject = jsonArray.getJSONObject(0);
                bookModel = new BookModel();
                bookModel.setBookId(finalObject.getInt("book_id"));
                bookModel.setBookTitle(finalObject.getString("book_title"));
                bookModel.setBookImage(finalObject.getString("book_img"));
                bookModel.setAuthor(finalObject.getString("author"));
                bookModel.setSubject(finalObject.getString("subject"));
                bookModel.setPages(finalObject.getInt("book_page"));
                bookModel.setType(finalObject.getString("type"));

                if (isBook) {
                    bookModel.setAvailable(finalObject.getInt("available"));
                    bookModel.setTotal(finalObject.getInt("total"));
                } else {
                    bookModel.setPdf(finalObject.getString("pdf"));
                }
                bookModel.setDescription(finalObject.getString("description"));

                bufferedReader.close();
                inputStream.close();
                httpURLConnection.disconnect();

                Log.i("NFO", "no err");

                return bookModel;

            } catch(IOException | JSONException e) {
                Log.e("ERR", "Error in getting name: " + e.getMessage());
                e.printStackTrace();
            }

            return null;
        }

        @Override
        protected void onPostExecute(BookModel bookModel) {
            super.onPostExecute(bookModel);

//            mLoadingDialog.dismiss();

            if (bookModel == null) {
                View view = findViewById(R.id.activity_book_information);
                Snackbar.make(view, ZHelper.NO_CONN_PROMPT, Snackbar.LENGTH_SHORT).show();
                return;
            }

            TextView tvBookTitle = (TextView) findViewById(R.id.tvBookTitle);
            ImageView imgBook = (ImageView) findViewById(R.id.imgBook);
            final ProgressBar progressBar = (ProgressBar) findViewById(R.id.progressBar);
            TextView tvAuthor = (TextView) findViewById(R.id.tvAuthor);
            TextView tvSubject = (TextView) findViewById(R.id.tvSubject);
            TextView tvPages = (TextView) findViewById(R.id.tvPages);
            TextView tvType = (TextView) findViewById(R.id.tvType);
            TextView tvAvailable= (TextView) findViewById(R.id.tvAvailable);
            TextView tvTotal = (TextView) findViewById(R.id.tvTotal);
            TextView tvDescription = (TextView) findViewById(R.id.tvDescription);
            Button btnDownload = (Button) findViewById(R.id.btnActionPdf);


            tvBookTitle.setText(bookModel.getBookTitle());
            String author = "Author: " + bookModel.getAuthor();
            ImageLoader.getInstance().displayImage(bookModel.getBookImage(), imgBook, new ImageLoadingListener() {
                @Override
                public void onLoadingStarted(String imageUri, View view) {
                    progressBar.setVisibility(View.VISIBLE);
                }

                @Override
                public void onLoadingFailed(String imageUri, View view, FailReason failReason) {
                    progressBar.setVisibility(View.GONE);
                }

                @Override
                public void onLoadingComplete(String imageUri, View view, Bitmap loadedImage) {
                    progressBar.setVisibility(View.GONE);
                }

                @Override
                public void onLoadingCancelled(String imageUri, View view) {
                    progressBar.setVisibility(View.GONE);
                }
            });
            tvAuthor.setText(author);
            String subject = "Subject: " + bookModel.getSubject();
            tvSubject.setText(subject);
            String pages = "Pages: " + bookModel.getPages();
            tvPages.setText(pages);
            String type = "Type: " + bookModel.getType();
            tvType.setText(type);

            if (isBook) {
                String available = "No. of Books Available: " + bookModel.getAvailable();
                tvAvailable.setText(available);
                String total = "Total No. of Books: : " + bookModel.getAvailable();
                tvTotal.setText(total);
                btnDownload.setVisibility(View.GONE);
            } else {
                tvAvailable.setVisibility(View.GONE);
                tvTotal.setVisibility(View.GONE);
                btnDownload.setVisibility(View.VISIBLE);
            }
            tvDescription.setText(bookModel.getDescription());

            new CheckFavoriteTask().execute();
        }
    }

    class CheckFavoriteTask extends AsyncTask<Void, Void, String> {

        Dialog mLoadingDialog;

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            mLoadingDialog = ProgressDialog.show(BookInformationActivity.this, "Please wait", "Loading...");
        }

        @Override
        protected String doInBackground(Void... params) {
            String getFav = "http://" + preferences.getString("serverIp", ZHelper.SERVER_IP)
                    + "/aubookcatalog/getbookfav.php";

            try {
                URL url = new URL(getFav);
                HttpURLConnection httpURLConnection = (HttpURLConnection) url.openConnection();
                httpURLConnection.setConnectTimeout(3000);
                httpURLConnection.setReadTimeout(3000);

                httpURLConnection.setRequestMethod("POST");
                httpURLConnection.setDoInput(true);
                httpURLConnection.setDoOutput(true);

                OutputStream outputStream = httpURLConnection.getOutputStream();
                BufferedWriter bufferedWriter = new BufferedWriter(
                        new OutputStreamWriter(outputStream, ZHelper.DB_ENCODE_TYPE));

                String postData =
                        URLEncoder.encode("studentId", ZHelper.DB_ENCODE_TYPE) + "=" +
                                URLEncoder.encode(studentId, ZHelper.DB_ENCODE_TYPE) + "&" +

                        URLEncoder.encode("bookId", ZHelper.DB_ENCODE_TYPE) + "=" +
                            URLEncoder.encode(bookId + "", ZHelper.DB_ENCODE_TYPE);

                bufferedWriter.write(postData);
                bufferedWriter.flush();
                bufferedWriter.close();
                outputStream.close();

                InputStream inputStream = httpURLConnection.getInputStream();
                BufferedReader bufferedReader = new BufferedReader(
                        new InputStreamReader(inputStream, ZHelper.DB_ENCODE_TYPE));

                StringBuilder builder = new StringBuilder();
                String line;
                while ((line = bufferedReader.readLine()) != null) {
                    builder.append(line);
                }

                bufferedReader.close();
                inputStream.close();
                httpURLConnection.disconnect();

                return builder.toString();

            }catch(IOException e) {
                Log.e("ERR", "Error in getting updatefav: " + e.getMessage());
                e.printStackTrace();
            }

            return null;
        }

        @Override
        protected void onPostExecute(String result) {
            super.onPostExecute(result);
            mLoadingDialog.dismiss();

            if (result.equals("success"))
                isFavorite = true;
            else if(result.equals("none"))
                isFavorite = false;
            else
                Log.e("ERR", "Error in retrieving fav");

            // update
            setFavoriteImage();
            Log.i("NFO", "Check result: " + result);
            Log.i("NFO", "Check Fav: " + isFavorite);
        }
    }

    class FavoriteTask extends AsyncTask<Void, String, String> {

        Dialog mLoadingDialog;

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            mLoadingDialog = ProgressDialog.show(BookInformationActivity.this, "Please wait", "Loading...");
        }

        @Override
        protected String doInBackground(Void... params) {
            String getName = "http://" + preferences.getString("serverIp", ZHelper.SERVER_IP)
                    + "/aubookcatalog/setbookfav.php";

            try {

                URL url = new URL(getName);
                HttpURLConnection httpURLConnection = (HttpURLConnection) url.openConnection();
                httpURLConnection.setConnectTimeout(3000);
                httpURLConnection.setReadTimeout(3000);

                httpURLConnection.setRequestMethod("POST");
                httpURLConnection.setDoInput(true);
                httpURLConnection.setDoOutput(true);

                OutputStream outputStream = httpURLConnection.getOutputStream();
                BufferedWriter bufferedWriter = new BufferedWriter(
                        new OutputStreamWriter(outputStream, ZHelper.DB_ENCODE_TYPE));

                String postData =
                        URLEncoder.encode("studentId", ZHelper.DB_ENCODE_TYPE) + "=" +
                                URLEncoder.encode(studentId + "", ZHelper.DB_ENCODE_TYPE) + "&" +

                                URLEncoder.encode("bookId", ZHelper.DB_ENCODE_TYPE) + "=" +
                                URLEncoder.encode(bookId + "", ZHelper.DB_ENCODE_TYPE) + "&" +

                                URLEncoder.encode("fav", ZHelper.DB_ENCODE_TYPE) + "=" +
                                URLEncoder.encode(isFavorite + "", ZHelper.DB_ENCODE_TYPE);

                bufferedWriter.write(postData);
                bufferedWriter.flush();
                bufferedWriter.close();
                outputStream.close();

                InputStream inputStream = httpURLConnection.getInputStream();
                BufferedReader bufferedReader = new BufferedReader(
                        new InputStreamReader(inputStream, ZHelper.DB_ENCODE_TYPE));

                StringBuilder builder = new StringBuilder();
                String line;
                while ((line = bufferedReader.readLine()) != null) {
                    builder.append(line);
                }

                bufferedReader.close();
                inputStream.close();
                httpURLConnection.disconnect();

                Log.i("NFO", "no err");

                return builder.toString();

            } catch(IOException  e) {
                Log.e("ERR", "Error in getting name: " + e.getMessage());
                e.printStackTrace();
            }

            return null;
        }

        @Override
        protected void onPostExecute(String result) {
            super.onPostExecute(result);

            mLoadingDialog.dismiss();

            if (result == null)
                return;

            if(result.equals("success"))
                isFavorite = !isFavorite;


            Log.i("NFO", "CHECK RESULT: " + result + " : fav: " + isFavorite);
            setFavoriteImage();

            View view = findViewById(R.id.activity_book_information);
            if(isFavorite)
                Snackbar.make(view, "Book is added to favorites.", Snackbar.LENGTH_SHORT).show();
            else
                Snackbar.make(view, "Book is removed from favorites.", Snackbar.LENGTH_SHORT).show();


        }
    }

}
