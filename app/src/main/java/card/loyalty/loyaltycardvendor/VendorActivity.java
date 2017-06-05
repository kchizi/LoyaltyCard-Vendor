package card.loyalty.loyaltycardvendor;

import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.design.widget.NavigationView;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import com.firebase.ui.auth.AuthUI;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;
import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import card.loyalty.loyaltycardvendor.data_models.LoyaltyCard;
import card.loyalty.loyaltycardvendor.data_models.LoyaltyOffer;
import card.loyalty.loyaltycardvendor.data_models.Subscription;

public class VendorActivity extends AppCompatActivity
        implements NavigationView.OnNavigationItemSelectedListener {

    private static final String TAG = "VendorActivity";
    private static final int RC_SIGN_IN = 123;


    public int mOfferIndex;


    public DatabaseReference mLoyaltyCardsRef;
    public DatabaseReference mSubscriptions;

    // wasLongPressed used in determining whether redeeming reward or purchasing
    public boolean mWasLongPressed;

    // List of Offers created
    public List<LoyaltyOffer> mOffers;

    // Drawer Objects
    private DrawerLayout drawerLayout;
    private ActionBarDrawerToggle drawerToggle;

    // Firebase Authentication
    private FirebaseAuth mFirebaseAuth;
    private FirebaseAuth.AuthStateListener mAuthStateListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_vendor);

        // Firebase Authentication Initialisation
        mFirebaseAuth = FirebaseAuth.getInstance();
        mLoyaltyCardsRef = FirebaseDatabase.getInstance().getReference().child("LoyaltyCards");

        /** Drawer Initialisation **/
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar); // toolbar id from app_bar_vendor
        setSupportActionBar(toolbar);

        drawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout); // drawer_layout from activity_vendor
        drawerToggle = new ActionBarDrawerToggle(
                this,           // host Activity
                drawerLayout,   // DrawerLayout Object
                toolbar,        // Sets Icon that opens the drawer
                R.string.navigation_drawer_open,    // Open Drawer description
                R.string.navigation_drawer_close);  // Close Drawer description
        drawerLayout.addDrawerListener(drawerToggle);
        drawerToggle.syncState();
        // Sets Navigaiotn Item Listeners
        // Main Items
        NavigationView nView = (NavigationView) findViewById(R.id.nav_view);
        nView.setNavigationItemSelectedListener(this);
        // Footer Items
        NavigationView nViewFooter = (NavigationView) findViewById(R.id.nav_view_footer);
        nViewFooter.setNavigationItemSelectedListener(this);

        // Firebase UI Authentication
        mAuthStateListener = new FirebaseAuth.AuthStateListener() {
            @Override
            public void onAuthStateChanged(@NonNull FirebaseAuth firebaseAuth) {
                FirebaseUser user = firebaseAuth.getCurrentUser();
                if (user != null) {
                    // User is signed in
                    Log.d(TAG, "onAuthStateChanged: is signed_in:" + user.getUid());
                    createDetails();
                } else {
                    // User is signed out
                    Log.d(TAG, "onAuthStateChanged: is signed_out");
                    startActivityForResult(
                            AuthUI.getInstance()
                                    .createSignInIntentBuilder()
                                    .setProviders(Arrays.asList(new AuthUI.IdpConfig.Builder(AuthUI.EMAIL_PROVIDER).build(),
                                            new AuthUI.IdpConfig.Builder(AuthUI.GOOGLE_PROVIDER).build()))
//                                    .setTheme(R.style.AppTheme_NoActionBar)
                                    .build(),
                            RC_SIGN_IN);
                }
            }
        };

        if (savedInstanceState == null) {
            Fragment frag = new OffersRecFragment();
            FragmentManager manager = getSupportFragmentManager();
            manager.beginTransaction()
                    .replace(R.id.content_vendor, frag)
                    .commit();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {

        super.onActivityResult(requestCode, resultCode, data);

        // Handling the Firebase UI Auth result
        if (requestCode == RC_SIGN_IN) {
            if (resultCode == RESULT_OK) {
                Toast.makeText(this, "Sign in successful", Toast.LENGTH_SHORT).show();
            } else if (resultCode == RESULT_CANCELED) {
                Toast.makeText(this, "Sign in cancelled", Toast.LENGTH_SHORT).show();
                finish();
            }
        } else {
            // Handle the QR scanning result
            IntentResult result = IntentIntegrator.parseActivityResult(requestCode, resultCode, data);
            if (result != null) {
                if (result.getContents() == null) {
                    Toast.makeText(this, "You cancelled the scanning", Toast.LENGTH_LONG).show();
                } else {
                    Log.d(TAG, "onActivityResult: result.getContents(): " + result.getContents());
                    LoyaltyOffer offer = mOffers.get(mOfferIndex);
                    processScanResult(offer, result.getContents());
                }
            }
        }
    }

    // processes the result of scanning
    private void processScanResult(final LoyaltyOffer offer, final String customerID) {
        String offerIDcustomerID = offer.getOfferID() + "_" + customerID;
        Log.d(TAG, "processScanResult: start");
        Query query = mLoyaltyCardsRef.orderByChild("offerID_customerID").equalTo(offerIDcustomerID);

        ValueEventListener listener = new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                if (dataSnapshot.exists()) {
                    for (DataSnapshot cardSnapshot : dataSnapshot.getChildren()) {
                        LoyaltyCard card = cardSnapshot.getValue(LoyaltyCard.class);
                        card.vendorID = mFirebaseAuth.getCurrentUser().getUid();
                        Log.d(TAG, "onDataChange: card purchase count: " + card.purchaseCount);
                        card.setCardID(cardSnapshot.getKey());
                        updateCard(card);
                    }
                } else {
                    createCard(offer, customerID);
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                // TODO on cancelled method
            }
        };

        query.addListenerForSingleValueEvent(listener);
        Log.d(TAG, "processScanResult: end");
    }

    public boolean isAuthenticated() {
        if (mFirebaseAuth.getCurrentUser() != null) {
            return true;
        } else {
            return false;
        }
    }

    // updates the card
    protected void updateCard(LoyaltyCard card) {
        // array used so final can be used. final used to access value in callback
        final Boolean redeem[] = new Boolean[1];
        redeem[0] = false;
        Log.d(TAG, "updateCard: start");
        // if not long pressed add to purchase count, otherwise redeem reward
        if (!mWasLongPressed) {
            card.addToPurchaseCount(1);
            subscribeCardHolder(card.customerID);
        } else {
            // redeem reward or display toast message if nothing to redeem
            redeem[0] = card.redeem();
            if (!redeem[0]) {
                Toast.makeText(this, "Nothing to Redeem!", Toast.LENGTH_SHORT).show();
                return;
            }
        }
        String key = card.retrieveCardID();
        if (key == null) key = mLoyaltyCardsRef.push().getKey();
        mLoyaltyCardsRef.child(key).setValue(card, new DatabaseReference.CompletionListener() {
            @Override
            public void onComplete(DatabaseError databaseError, DatabaseReference databaseReference) {
                if (databaseError != null) {
                    Toast.makeText(VendorActivity.this, "FAILED TO UPDATE. TRY AGAIN!", Toast.LENGTH_SHORT).show();
                } else {
                    if (redeem[0]) {
                        Toast.makeText(VendorActivity.this, "Reward Successfully Redeemed!", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(VendorActivity.this, "Purchase Count Successfully Updated", Toast.LENGTH_SHORT).show();
                    }
                    Log.d(TAG, "onComplete: success");
                }
            }
        });
        Log.d(TAG, "updateCard: end");
    }

    // creates a new card
    private void createCard(LoyaltyOffer offer, String customerID) {
        Log.d(TAG, "createCard: start");
        LoyaltyCard card = new LoyaltyCard(offer.getOfferID(), customerID, offer.purchasesPerReward);
        card.vendorID = mFirebaseAuth.getCurrentUser().getUid();
        updateCard(card);
        subscribeCardHolder(customerID);
        Log.d(TAG, "createCard: end");
    }

    // subscribe user to push notifications
    private void subscribeCardHolder(String customerId) {

        // get the vendors uid to use as database key within Subscriptions node
        String keyVendor = mFirebaseAuth.getCurrentUser().getUid();

        // set the database reference to
        mSubscriptions = FirebaseDatabase.getInstance().getReference().child("Subscriptions");

        HashMap<String, Object> subscription = new HashMap<>();
        subscription.put(customerId, new Subscription(Boolean.TRUE));
        mSubscriptions.child(keyVendor).updateChildren(subscription);
    }

    private void createDetails() {
        Log.d(TAG, "createDetails: start");
        final String Uid = mFirebaseAuth.getCurrentUser().getUid();
        final Query query = FirebaseDatabase.getInstance().getReference().child("Vendors");
        query.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                //checks if current signed in users UID exists in Vendor
                if (snapshot.hasChild(Uid)) {

                    Log.d(TAG, "createDetails: exists");
                    //if no child with the same UID exists the user is prompted to create a new one
                } else {
                    Log.d(TAG, "createDetails: creating");
                    launchVendorFragment();

                }

                Log.d(TAG, "createDetails: end");
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {

            }

        });
    }

    //Calls the launchVendorDetailsActivity which will prompt the user with text fields, changes made will be added to the database
    public void updateDetails() {
        launchVendorFragment();

    }


    // On resuming activity
    @Override
    protected void onResume() {

        super.onResume();

        // Add the firebase auth state listener
        mFirebaseAuth.addAuthStateListener(mAuthStateListener);
    }

    // On pausing activity
    @Override
    protected void onPause() {

        super.onPause();

        // Remove the firebase auth state listener
        mFirebaseAuth.removeAuthStateListener(mAuthStateListener);
    }

    @Override
    public void onBackPressed() {

        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);

        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.vendor_landing, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    /**
     * Drawer Functionality
     **/
    // Add desired functionality in each switch case
    @Override
    public boolean onNavigationItemSelected(MenuItem item) {

        Fragment current = null;

        // Handle navigation view item clicks here.
        switch (item.getItemId()) {
            case R.id.nav_offers:
                // launches Offes Fragment
                current = new OffersRecFragment();
                break;
            case R.id.nav_add_offer:
                // Launches Add Offer Fragment
                current = new AddOfferFragment();
                break;
            case R.id.nav_push_promos:
                // Launches Push Promo Fragment
                current = new PushPromotionFragment();
                break;

            case R.id.nav_update_details:
                // Launches Your Fragment
                current = new AddDetailFragment();
                break;

            case R.id.nav_sign_out:
                // FirebaseUI Sign Out
                AuthUI.getInstance().signOut(this);
                break;
        }

        if (current == null) return true;

        // Launches fragment
        FragmentManager fragmentManager = getSupportFragmentManager();
        fragmentManager.beginTransaction()
                .replace(R.id.content_vendor, current)
                .addToBackStack(null)
                .commit();
        // Closes drawer when item is pressed
        drawerLayout.closeDrawer(GravityCompat.START);
        return true;
    }

    private void launchVendorFragment() {
        Fragment frag = new AddDetailFragment();
        FragmentManager manager = getSupportFragmentManager();
        manager.beginTransaction()
                .replace(R.id.content_vendor, frag)
                .addToBackStack(null)
                .commit();
    }
}


