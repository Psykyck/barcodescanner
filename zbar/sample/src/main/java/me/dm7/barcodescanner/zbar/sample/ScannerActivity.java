package me.dm7.barcodescanner.zbar.sample;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.view.MenuItemCompat;
import android.support.v7.app.ActionBarActivity;
import android.util.Patterns;
import android.view.Menu;
import android.view.MenuItem;

import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import me.dm7.barcodescanner.zbar.BarcodeFormat;
import me.dm7.barcodescanner.zbar.Result;
import me.dm7.barcodescanner.zbar.ZBarScannerView;

public class ScannerActivity extends ActionBarActivity implements MessageDialogFragment.MessageDialogListener,
        ZBarScannerView.ResultHandler, FormatSelectorDialogFragment.FormatSelectorDialogListener,
        CameraSelectorDialogFragment.CameraSelectorDialogListener {
    private static final String FLASH_STATE = "FLASH_STATE";
    private static final String AUTO_FOCUS_STATE = "AUTO_FOCUS_STATE";
    private static final String SELECTED_FORMATS = "SELECTED_FORMATS";
    private static final String CAMERA_ID = "CAMERA_ID";
    private ZBarScannerView mScannerView;
    private boolean mFlash;
    private boolean mAutoFocus;
    private ArrayList<Integer> mSelectedIndices;
    private int mCameraId = -1;
    private ProgressDialog mLoadingDialog;
    private String mGoogleMessage;
    private String mWOTMessage;

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        if(state != null) {
            mFlash = state.getBoolean(FLASH_STATE, false);
            mAutoFocus = state.getBoolean(AUTO_FOCUS_STATE, true);
            mSelectedIndices = state.getIntegerArrayList(SELECTED_FORMATS);
            mCameraId = state.getInt(CAMERA_ID, -1);
        } else {
            mFlash = false;
            mAutoFocus = true;
            mSelectedIndices = null;
            mCameraId = -1;
        }

        mScannerView = new ZBarScannerView(this);
        setupFormats();
        setContentView(mScannerView);
    }

    @Override
    public void onResume() {
        super.onResume();
        mScannerView.setResultHandler(this);
        mScannerView.startCamera(mCameraId);
        mScannerView.setFlash(mFlash);
        mScannerView.setAutoFocus(mAutoFocus);
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(FLASH_STATE, mFlash);
        outState.putBoolean(AUTO_FOCUS_STATE, mAutoFocus);
        outState.putIntegerArrayList(SELECTED_FORMATS, mSelectedIndices);
        outState.putInt(CAMERA_ID, mCameraId);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuItem menuItem;

        if(mFlash) {
            menuItem = menu.add(Menu.NONE, R.id.menu_flash, 0, R.string.flash_on);
        } else {
            menuItem = menu.add(Menu.NONE, R.id.menu_flash, 0, R.string.flash_off);
        }
        MenuItemCompat.setShowAsAction(menuItem, MenuItem.SHOW_AS_ACTION_ALWAYS);


        if(mAutoFocus) {
            menuItem = menu.add(Menu.NONE, R.id.menu_auto_focus, 0, R.string.auto_focus_on);
        } else {
            menuItem = menu.add(Menu.NONE, R.id.menu_auto_focus, 0, R.string.auto_focus_off);
        }
        MenuItemCompat.setShowAsAction(menuItem, MenuItem.SHOW_AS_ACTION_ALWAYS);

        //menuItem = menu.add(Menu.NONE, R.id.menu_formats, 0, R.string.formats);
        //MenuItemCompat.setShowAsAction(menuItem, MenuItem.SHOW_AS_ACTION_ALWAYS);

        menuItem = menu.add(Menu.NONE, R.id.menu_camera_selector, 0, R.string.select_camera);
        MenuItemCompat.setShowAsAction(menuItem, MenuItem.SHOW_AS_ACTION_ALWAYS);

        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle presses on the action bar items
        switch (item.getItemId()) {
            case R.id.menu_flash:
                mFlash = !mFlash;
                if(mFlash) {
                    item.setTitle(R.string.flash_on);
                } else {
                    item.setTitle(R.string.flash_off);
                }
                mScannerView.setFlash(mFlash);
                return true;
            case R.id.menu_auto_focus:
                mAutoFocus = !mAutoFocus;
                if(mAutoFocus) {
                    item.setTitle(R.string.auto_focus_on);
                } else {
                    item.setTitle(R.string.auto_focus_off);
                }
                mScannerView.setAutoFocus(mAutoFocus);
                return true;
//            case R.id.menu_formats:
//                DialogFragment fragment = FormatSelectorDialogFragment.newInstance(this, mSelectedIndices);
//                fragment.show(getSupportFragmentManager(), "format_selector");
//                return true;
            case R.id.menu_camera_selector:
                mScannerView.stopCamera();
                DialogFragment cFragment = CameraSelectorDialogFragment.newInstance(this, mCameraId);
                cFragment.show(getSupportFragmentManager(), "camera_selector");
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void handleResult(Result rawResult) {
        mLoadingDialog = new ProgressDialog(this);
        mLoadingDialog.isIndeterminate();
        mLoadingDialog.setTitle("Checking blacklists...");
        mLoadingDialog.setCancelable(false);
        mLoadingDialog.setCanceledOnTouchOutside(false);
        mLoadingDialog.show();

        try {
            Uri notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            Ringtone r = RingtoneManager.getRingtone(getApplicationContext(), notification);
            r.play();
        } catch (Exception e) {}
        //showMessageDialog("Contents = " + rawResult.getContents() + ", Format = " + rawResult.getBarcodeFormat().getName());
        Intent i = new Intent(Intent.ACTION_VIEW);
        String res = "",resWOT="";
        Intent current = getIntent();
        String url = rawResult.getContents().replace("https://", "");
        url = url.replace("http://","");
        if (Patterns.WEB_URL.matcher(url).matches()) {
            try {
                res = new sendRequest().execute(url).get();
                resWOT = new sendRequestWOT().execute(url).get();
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else{
            mLoadingDialog.dismiss();
            showMessageDialog("Not a Valid URL");
        }

        String message="";
        switch (res){
            case "204":
//              i.setData(Uri.parse(rawResult.getContents()));
//              startActivity(i);
                message = "Google okay'd this URL.";
                break;
            case "200":
                message ="Google blocks this URL. Response message: \"" + mGoogleMessage +"\"";
                break;
            default:
                message ="Failed to check Google's Blacklist.";
        }

        switch (resWOT){
            case "200":
                try {
                    JSONObject m = new JSONObject(mWOTMessage);
                    String temp = m.keys().next();
                    JSONObject o= m.getJSONObject(temp);
                    message+="\n\nWOT's response message:\nTrustworthiness: "+o.getJSONArray("0").getInt(0)+"%\nChild safety: "+o.getJSONArray("4").getInt(0)+"%";

                    if(o.has("categories")) {
                        Iterator<String> c = o.getJSONObject("categories").keys();
                        message += "\n\nCategories:\n";
                        while (c.hasNext()) switch (c.next()) {
                            case "501":
                                message += "Good site\n";
                                break;
                            case "101":
                                message += "Malware or viruses\n";
                                break;
                            case "102":
                                message += "Poor customer experience\n";
                                break;
                            case "103":
                                message += "Phishing\n";
                                break;
                            case "104":
                                message += "Scam\n";
                                break;
                            case "105":
                                message += "Potentially illegal\n";
                                break;
                            case "201":
                                message += "Misleading claims or unethical\n";
                                break;
                            case "202":
                                message += "Privacy risks\n";
                                break;
                            case "203":
                                message += "Suspicious\n";
                                break;
                            case "204":
                                message += "Hate, discrimination\n";
                                break;
                            case "205":
                                message += "Spam\n";
                                break;
                            case "206":
                                message += "Potentially unwanted programs\n";
                                break;
                            case "207":
                                message += "Ads/pop-ups\n";
                                break;
                            case "301":
                                message += "Online tracking\n";
                                break;
                            case "302":
                                message += "Alternative or controversial medicine\n";
                                break;
                            case "303":
                                message += "Opinions, religion, politics\n";
                                break;
                            case "304":
                                message += "Other\n";
                                break;
                            default:
                        }
                    }

                }catch(Exception e){
                    message +="\n\nFailed to check WOT's Blacklist.\n\n";
                }
                break;
            default:
                message +="Failed to check WOT's Blacklist.\n\n";
        }

        showDialog("Launch " + url + "?", i, url, message, current);
    }

    public void onLaunchBrowser(Intent i, String url) {
        if (Patterns.WEB_URL.matcher(url).matches()) {
            i.setData(Uri.parse("http://" +url));
            startActivity(i);
        } else {
            showMessageDialog("Not a Valid URL or is blocked");
        }

    }
    private class sendRequest extends AsyncTask<String, String, String> {
        protected String doInBackground(String... arg){
            String result;
            StringBuffer sb = new StringBuffer();
            InputStream is = null;
            String baseURL = "https://sb-ssl.google.com/safebrowsing/api/lookup";

            String arguments = "";
            try {
                arguments += URLEncoder.encode("client", "UTF-8") + "=" + URLEncoder.encode("InfoSec", "UTF-8") + "&";
                arguments += URLEncoder.encode("key", "UTF-8") + "=" + URLEncoder.encode("AIzaSyCCn9EpFwEk0rnnrxgQue9H40iGI_z2rBw", "UTF-8") + "&";
                arguments += URLEncoder.encode("appver", "UTF-8") + "=" + URLEncoder.encode("1.5.2", "UTF-8") + "&";
                arguments += URLEncoder.encode("pver", "UTF-8") + "=" + URLEncoder.encode("3.1", "UTF-8") + "&";
                arguments += URLEncoder.encode("url", "UTF-8") + "=" + URLEncoder.encode(arg[0], "UTF-8");

                // Construct the url object representing cgi script
                URL url = new URL(baseURL + "?" + arguments);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                sb.append(connection.getResponseCode());
                mGoogleMessage = connection.getResponseMessage();
            } catch(IOException e){}
            result = sb.toString();
            if (is != null) {
                try {
                    is.close();
                } catch (IOException e) {
                    //Log.i(TAG, "Error closing InputStream");
                }
            }

            return result;
        }
    }

    private class sendRequestWOT extends AsyncTask<String, String, String> {
        protected String doInBackground(String... arg){
            String result;
            StringBuffer sb = new StringBuffer();
            InputStream is = null;
            String baseURL = "http://api.mywot.com/0.4/public_link_json2";

            String arguments = "";
            try {
                arguments += URLEncoder.encode("hosts", "UTF-8") + "=" + URLEncoder.encode(arg[0]+"/", "UTF-8") + "&";
                arguments += URLEncoder.encode("key", "UTF-8") + "=" + URLEncoder.encode("b667210b38604f57ff657406d23679889aa2f9dc", "UTF-8");

                // Construct the url object representing cgi script
                URL url = new URL(baseURL + "?" + arguments);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                sb.append(connection.getResponseCode());

                is = new BufferedInputStream(connection.getInputStream());
                BufferedReader br = new BufferedReader(new InputStreamReader(is));
                mWOTMessage = br.readLine();
            } catch(IOException e){}
            result = sb.toString();
            if (is != null) {
                try {
                    is.close();
                } catch (IOException e) {
                    //Log.i(TAG, "Error closing InputStream");
                }
            }

            mLoadingDialog.dismiss();
            return result;
        }
    }

    public void showDialog(String title, final Intent i, final String url, String message,final Intent current) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(title)
                .setCancelable(false)
                .setPositiveButton("Launch in browser", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        onLaunchBrowser(i, url);
                    }
                })
                .setNegativeButton("Don't Launch", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        dialog.cancel();
                        onBlock( url, current);
                    }
                })
                .setMessage(message);
        AlertDialog alert = builder.create();
        alert.show();
    }

    public void onBlock( String url, Intent i) {

        onResume();

    }

    public void showMessageDialog(String message) {
        DialogFragment fragment = MessageDialogFragment.newInstance("Scan Results", message, this);
        fragment.show(getSupportFragmentManager(), "scan_results");
    }

    public void closeMessageDialog() {
        closeDialog("scan_results");
    }

    public void closeFormatsDialog() {
        closeDialog("format_selector");
    }

    public void closeDialog(String dialogName) {
        FragmentManager fragmentManager = getSupportFragmentManager();
        DialogFragment fragment = (DialogFragment) fragmentManager.findFragmentByTag(dialogName);
        if(fragment != null) {
            fragment.dismiss();
        }
    }

    @Override
    public void onDialogPositiveClick(DialogFragment dialog) {
        // Resume the camera
        mScannerView.startCamera(mCameraId);
        mScannerView.setFlash(mFlash);
        mScannerView.setAutoFocus(mAutoFocus);
    }

    @Override
    public void onFormatsSaved(ArrayList<Integer> selectedIndices) {
        mSelectedIndices = selectedIndices;
        setupFormats();
    }

    @Override
    public void onCameraSelected(int cameraId) {
        mCameraId = cameraId;
        mScannerView.startCamera(mCameraId);
        mScannerView.setFlash(mFlash);
        mScannerView.setAutoFocus(mAutoFocus);
    }


    public void setupFormats() {
        List<BarcodeFormat> formats = new ArrayList<BarcodeFormat>();
        if (mSelectedIndices == null || mSelectedIndices.isEmpty()) {
            mSelectedIndices = new ArrayList<Integer>();
//            for(int i = 0; i < BarcodeFormat.ALL_FORMATS.size(); i++) {
//                mSelectedIndices.add(i);
//            }

            mSelectedIndices.add(BarcodeFormat.QRCODE.getId());
        }

//        for(int index : mSelectedIndices) {
//            formats.add(BarcodeFormat.ALL_FORMATS.get(index));
//        }

        formats.add(BarcodeFormat.QRCODE);

        if (mScannerView != null) {
            mScannerView.setFormats(formats);
        }

        InputStream is = null;
        // Only display the first 500 characters of the retrieved
        // web page content.
        int len = 500;

        try {
            URL url = new URL("http://api.mywot.com/0.4/public_link_json2?hosts=goooogle.com/&key=b667210b38604f57ff657406d23679889aa2f9dc");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setReadTimeout(10000 /* milliseconds */);
            conn.setConnectTimeout(15000 /* milliseconds */);
            conn.setRequestMethod("GET");
            conn.setDoInput(true);
            // Starts the query
            conn.connect();
            int response = conn.getResponseCode();
            is = conn.getInputStream();

            // Convert the InputStream into a string
            //String contentAsString = readIt(is, len);

            // Makes sure that the InputStream is closed after the app is
            // finished using it.
        } catch (Exception ee) {
            int i = 0;
        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (Exception e) {

                }
            }
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        mScannerView.stopCamera();
        closeMessageDialog();
        closeFormatsDialog();
    }
}
