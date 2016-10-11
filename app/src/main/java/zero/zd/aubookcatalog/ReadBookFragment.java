package zero.zd.aubookcatalog;

import android.app.Fragment;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import zero.zd.aubookcatalog.adapter.DownloadedPdfAdapter;


public class ReadBookFragment extends Fragment {

    private List<String> pdfList;
    DownloadedPdfAdapter adapter;
    private TextView txtInfo;
    private TextView txtNone;
    private ListView listView;

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_read_book, container, false);
        listView = (ListView) view.findViewById(R.id.listView);
        txtInfo = (TextView) view.findViewById(R.id.txtInfo);
        txtNone = (TextView) view.findViewById(R.id.txtNone);
        initDownloadedPdf();

        updateList();

        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {

                String title = pdfList.get(position);

                File file = ZHelper.getInstance().getPdf();
                File pdf = new File(file.getAbsolutePath() + "/" + title + ".pdf");
                Log.i("NFO", "PATH: " + file);
                Log.i("NFO", "PDF: " + pdf);
                Intent i = new Intent(Intent.ACTION_VIEW);
                i.setDataAndType(Uri.fromFile(pdf), "application/pdf");
                i.setFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);
                startActivity(i);
            }
        });

        listView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                String title = pdfList.get(position);
                File f = new File(ZHelper.getInstance().getPdf().getAbsolutePath() + "/" + title + ".pdf");
                Log.i("NFO", "File: " + f);
                if (f.delete()) {
                    Log.i("NFO", "deleted");
                } else {
                    Log.i("NFO", "failed deletion");
                }
                Toast.makeText(getActivity().getApplicationContext(), "PDF Deleted", Toast.LENGTH_SHORT).show();
                initDownloadedPdf();
                updateList();
                adapter.notifyDataSetChanged();
                return true;
            }
        });

        return view;
    }
    private void initDownloadedPdf() {
        pdfList = new ArrayList<>();

        File f = new File(ZHelper.getInstance().getPdf().getAbsolutePath());
        File files[] = f.listFiles();

        if (files.length != 0) {
            for (File file : files) {
                String name = file.getName();
                name = name.substring(0, name.length() - 4);
                pdfList.add(name);
            }
        }

    }

    private void updateList() {
        if(!pdfList.isEmpty()) {
            adapter = new DownloadedPdfAdapter(getActivity().getApplicationContext(),
                    R.layout.downloaded_pdf_layout, pdfList);
            listView.setAdapter(adapter);
            txtNone.setVisibility(View.GONE);
            listView.setVisibility(View.VISIBLE);
            txtInfo.setVisibility(View.VISIBLE);
        } else {
            txtNone.setVisibility(View.VISIBLE);
            listView.setVisibility(View.GONE);
            txtInfo.setVisibility(View.GONE);
        }
    }

}
