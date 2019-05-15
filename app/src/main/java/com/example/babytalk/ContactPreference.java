package com.example.babytalk;
/**
 * CLS_Prefs_Contact class
 *
 * This is the class that allows for a custom Contact Picker Preference
 * (auto refresh summary).
 *
 * @category    Custom Preference
 * @author      Paranoid Eyes
 * @copyright   Paranoid Eyes
 * @version     1.0
 */

/* ---------------------------------- Imports ------------------------------- */

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.preference.RingtonePreference;
import android.provider.ContactsContract;
import android.util.AttributeSet;
import android.util.Log;

/**
 * @attr ref android.R.styleable#ContactPreference_contactInfo
 */
public class ContactPreference extends RingtonePreference
{

    /* ----------------------------- Constants ------------------------------ */

    /*
    private static final String nsContact =
        "http://schemas.android.com/apk/res/com.lucacrisi.contactpreference_rt";
    */
    private static final String nsContact =
            "http://schemas.android.com/apk/res-auto";

    /* ----------------------------- Variables ------------------------------ */

    //private static Contact_Info contactInfo = null;
    private int contactInfo = 0;
    private final String defaultSummary = "---";

    /* ------------------------------ Objects ------------------------------- */

    private static Context ctx = null;

    private static SharedPreferences prefs = null;

    /* ---------------------------- Constructors ---------------------------- */

    public ContactPreference(final Context context, final AttributeSet attrs)
    {
        super(context, attrs);

        ctx = context;

        // Read attributes
        contactInfo = attrs.getAttributeIntValue(nsContact, "contactInfo", 0);

        //System.out.println("Constructor - " + contactInfo);

        prefs = PreferenceManager.getDefaultSharedPreferences(ctx);

        summary_Update();
    }

    /* ----------------------------- Overrides ------------------------------ */

    @Override
    public final boolean onActivityResult
            (final int requestCode, final int resultCode, final Intent data)
    {
        boolean result = false;
        if (super.onActivityResult(requestCode, resultCode, data))
        {
            if (data != null)
            {
                //
                getContactInfo(data);

                //
                final Uri uri = data.getData();
                if (callChangeListener(uri != null ? uri.toString() : ""))
                {
                    result = true;
                }
            }
        }
        return result;
    }
    @Override
    protected final void onPrepareRingtonePickerIntent(final Intent tnt)
    {
        tnt.setAction(Intent.ACTION_PICK);
        tnt.setData(ContactsContract.Contacts.CONTENT_URI);
    }


    /* ------------------------------ Methods ------------------------------- */

    private final void getContactInfo(final Intent data)
    {
        //final String noData = getString(R.string.missing_data);
        //String result = noData;
        String result = "";

        final Cursor cur =
                ctx.getContentResolver().query(data.getData(), null, null, null, null);
        while (cur.moveToNext())
        {
            final String contactId =
                    cur.getString
                            (
                                    cur.getColumnIndex
                                            (
                                                    ContactsContract.Contacts._ID
                                            )
                            );

            //System.out.println("getContactInfo - " + contactInfo);

                // Find the phone numbers
                String hasPhone = cur.getString
                        (
                                cur.getColumnIndex
                                        (
                                                ContactsContract.Contacts.HAS_PHONE_NUMBER
                                        )
                        );
                if (hasPhone.equalsIgnoreCase("1"))
                {
                    hasPhone = "true";
                }
                else
                {
                    hasPhone = "false" ;
                }
                if (Boolean.parseBoolean(hasPhone))
                {
                    final Cursor phones = ctx.getContentResolver().query
                            (
                                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                                    null,
                                    ContactsContract.CommonDataKinds.Phone.CONTACT_ID +
                                            " = " + contactId, null, null
                            );
                    String number = ""; //noData;
                    if (phones.getCount() > 0)
                    {
                        phones.moveToFirst();
                        number = phones.getString
                                (
                                        phones.getColumnIndex
                                                (
                                                        ContactsContract.CommonDataKinds.Phone.NUMBER
                                                )
                                );
                    }
                    phones.close();
                    result = number;
                    Log.i("phone number",number);
                }
        }
        cur.close();

        // Here do the magic and put the value into the Preferences
        setting_Write(getKey(), result);

        summary_Update();
    }

    private final static void setting_Write(final String key, final String value)
    {
        // Write the value
        prefs.edit().putString(key, value).commit();
    }

    private final void summary_Update()
    {
        // Read the value and set the summary
        String str = prefs.getString(getKey(), "");
        if ("".equals(str))
        {
            str = defaultSummary;
        }
        Log.i("Phone number",str);
        setSummary("Phone number to be called when monitoring triggers.\nCurrent setting: "+str);
    }
}