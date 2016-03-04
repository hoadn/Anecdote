package io.gresse.hugo.anecdote.fragment;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatDialogFragment;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.squareup.otto.Subscribe;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import butterknife.Bind;
import butterknife.ButterKnife;
import butterknife.OnClick;
import io.gresse.hugo.anecdote.R;
import io.gresse.hugo.anecdote.adapter.ViewHolderListener;
import io.gresse.hugo.anecdote.adapter.WebsiteChooserAdapter;
import io.gresse.hugo.anecdote.event.BusProvider;
import io.gresse.hugo.anecdote.event.WebsitesChangeEvent;
import io.gresse.hugo.anecdote.event.network.NetworkConnectivityChangeEvent;
import io.gresse.hugo.anecdote.model.Website;
import io.gresse.hugo.anecdote.util.SpStorage;
import io.gresse.hugo.anecdote.util.Utils;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Display a list of website from Firebase so the user can choose which website he want to load after initial
 * application launch
 * <p/>
 * Created by Hugo Gresse on 03/03/16.
 */
public class WebsiteChooserDialogFragment extends AppCompatDialogFragment implements ViewHolderListener {

    public static final String TAG = WebsiteChooserDialogFragment.class.getSimpleName();

    @Bind(R.id.recyclerView)
    public RecyclerView mRecyclerView;

    protected OkHttpClient mOkHttpClient;

    @Nullable
    protected Request               mFailRequest;
    private   List<Website>         mWebsites;
    private   WebsiteChooserAdapter mAdapter;
    private   List<Website>         mSelectedWebsites;

    public static WebsiteChooserDialogFragment newInstance() {
        return new WebsiteChooserDialogFragment();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_dialog_websitechooser, container);
        ButterKnife.bind(this, view);
        return view;
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Init dialog stuff
        getDialog().setTitle(R.string.dialog_websitechooser_title);
//        int width = (int) (getResources().getDisplayMetrics().widthPixels * 0.90);
//        getDialog().getWindow().setLayout(width, getDialog().getWindow().getAttributes().height);

        mSelectedWebsites = new ArrayList<>();
        mRecyclerView.setLayoutManager(new LinearLayoutManager(getActivity()));
        mAdapter = new WebsiteChooserAdapter(this);
        mRecyclerView.setAdapter(mAdapter);

        mOkHttpClient = new OkHttpClient();

        Request request = new Request.Builder()
                .url("https://crackling-inferno-9530.firebaseio.com/websites.json")
                .header("User-Agent", Utils.getUserAgent())
                .build();

        getWebsites(request);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        ButterKnife.unbind(this);
    }

    @Override
    public void onResume() {
        super.onResume();
        BusProvider.getInstance().register(this);
    }

    @Override
    public void onPause() {
        super.onPause();
        BusProvider.getInstance().unregister(this);
    }

    @OnClick(R.id.saveButton)
    public void onSaveButtonClicked(){
        SpStorage.saveWebsites(getActivity(), mSelectedWebsites);
        SpStorage.setFirstLaunch(getActivity(), false);
        BusProvider.getInstance().post(new WebsitesChangeEvent());
        getDialog().dismiss();
    }

    /***************************
     * Inner method
     ***************************/

    private void getWebsites(final Request request) {
        mFailRequest = null;
        mOkHttpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                e.printStackTrace();
                mFailRequest = request;
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                // We are not on main thread
                String websitesString = response.body().string();

                websitesString = java.net.URLDecoder.decode(websitesString, "UTF-8");

                Type type = new TypeToken<HashMap<String, Website>>() {
                }.getType();

                Map<String, Website> websites = new Gson().fromJson(websitesString, type);

                if (websites == null || websites.isEmpty()) {
                    return;
                }

                mWebsites = new ArrayList<>();

                for (Map.Entry<String, Website> entry : websites.entrySet()) {
                    mWebsites.add(entry.getValue());
                }


                Collections.sort(mWebsites, new Comparator<Website>() {
                    @Override
                    public int compare(Website website, Website website2) {
                        if (website.like > website2.like) {
                            return -1;
                        } else if (website.like < website2.like) {
                            return 1;
                        }
                        return 0;
                    }
                });

                if (getActivity() != null) {
                    new Handler(Looper.getMainLooper()).post(new Runnable() {
                        @Override
                        public void run() {
                            mAdapter.setData(mWebsites);
                        }
                    });
                }
            }
        });

    }

    /***************************
     * Event
     ***************************/

    @Subscribe
    public void onConnectivityChange(NetworkConnectivityChangeEvent event) {
        if (mFailRequest != null) {
            getWebsites(mFailRequest);
        }
    }

    /***************************
     * ViewHolderListener
     ***************************/

    @Override
    public void onClick(Object object) {
        if (!(object instanceof Website)) {
            return;
        }
        Website website = (Website) object;
        if(mSelectedWebsites.contains(website)){
            mSelectedWebsites.remove(website);
        } else {
            mSelectedWebsites.add(website);
        }
    }

    @Override
    public void onLongClick(Object object) {

    }
}