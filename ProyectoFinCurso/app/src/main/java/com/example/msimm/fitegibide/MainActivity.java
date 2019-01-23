package com.example.msimm.fitegibide;


import android.content.Intent;
import android.content.IntentSender;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;


import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.fitness.Fitness;
import com.google.android.gms.fitness.data.DataPoint;
import com.google.android.gms.fitness.data.DataSource;
import com.google.android.gms.fitness.data.DataType;
import com.google.android.gms.fitness.data.Field;
import com.google.android.gms.fitness.data.Value;
import com.google.android.gms.fitness.request.DataSourcesRequest;
import com.google.android.gms.fitness.request.OnDataPointListener;
import com.google.android.gms.fitness.request.SensorRequest;
import com.google.android.gms.fitness.request.SessionInsertRequest;
import com.google.android.gms.fitness.result.DataSourcesResult;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;


public class MainActivity extends AppCompatActivity implements OnDataPointListener,GoogleApiClient.ConnectionCallbacks,GoogleApiClient.OnConnectionFailedListener {

    public static final String LOG_TAG = "BasicHistoryApi";
    public static final String LOG_TAG_FB = "Firebase";
    public static final String LOG_TAG_CONSULTA = "Consulta";
    public static final String LOG_TAG_CONTADOR = "Contador";
    int GOOGLE_FIT_PERMISSIONS_REQUEST_CODE = 0533;
    private final String SESSION_NAME = "LogRunActivity";
    SessionInsertRequest insertRequest;


    private static final int REQUEST_OAUTH = 1;
    private static final String AUTH_PENDING = "auth_state_pending";
    private boolean authInProgress = false;
    private GoogleApiClient mApiClient;
    private static final int RC_SIGN_IN = 9001;
    private String documentID = "null";
    private DocumentReference dr = null;
    int numUsuarios;
    int pos;
    TextView cifraPasos;
    TextView cifraRanquing;
    Button actualizar;
    FirebaseFirestore db = FirebaseFirestore.getInstance();
    Map<String, Object> pasos = new HashMap<>();


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        if (savedInstanceState != null) {
            authInProgress = savedInstanceState.getBoolean(AUTH_PENDING);
        }

        mApiClient = new GoogleApiClient.Builder(this)
                .addApi(Fitness.SENSORS_API)
                .addScope(Fitness.SCOPE_ACTIVITY_READ_WRITE) //new Scope(Scopes.FITNESS_ACTIVITY_READ_WRITE))
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .build();
        //Actualizar el ranquin mendiante el boton "Actualizar"
        actualizar =  findViewById(R.id.bt_actualizar);
        actualizar.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                refrescarRanking();
            }
        });

    }

    @Override
    public void onConnected(Bundle bundle) {

        DataSourcesRequest dataSourceRequest = new DataSourcesRequest.Builder()
                .setDataTypes( DataType.TYPE_STEP_COUNT_CUMULATIVE )
                .setDataSourceTypes( DataSource.TYPE_RAW )
                .build();

        ResultCallback<DataSourcesResult> dataSourcesResultCallback = new ResultCallback<DataSourcesResult>() {
            @Override
            public void onResult(DataSourcesResult dataSourcesResult) {
                for( DataSource dataSource : dataSourcesResult.getDataSources() ) {
                    if( DataType.TYPE_STEP_COUNT_CUMULATIVE.equals( dataSource.getDataType() ) ) {
                        registerFitnessDataListener(dataSource, DataType.TYPE_STEP_COUNT_CUMULATIVE);
                    }
                }
            }
        };

        Fitness.SensorsApi.findDataSources(mApiClient, dataSourceRequest)
                .setResultCallback(dataSourcesResultCallback);
    }

    private void registerFitnessDataListener(DataSource dataSource, DataType dataType) {

        SensorRequest request = new SensorRequest.Builder()
                .setDataSource( dataSource )
                .setDataType( dataType )
                .setSamplingRate( 3, TimeUnit.SECONDS )
                .build();

        Fitness.SensorsApi.add( mApiClient, request, this )
                .setResultCallback(new ResultCallback<Status>() {
                    @Override
                    public void onResult(Status status) {
                        if (status.isSuccess()) {
                            Log.e( "GoogleFit", "SensorApi successfully added" );
                        }
                    }
                });
    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
        if( !authInProgress ) {
            try {
                authInProgress = true;
                connectionResult.startResolutionForResult( MainActivity.this, REQUEST_OAUTH );
            } catch(IntentSender.SendIntentException e ) {

            }
        } else {
            Log.e( "GoogleFit", "authInProgress" );
        }
    }

    @Override
    public void onDataPoint(DataPoint dataPoint) {
         final Field field = dataPoint.getDataType().getFields().get(0);
            final Value value = dataPoint.getValue( field );
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    //Toast.makeText(getApplicationContext(), "Field: " + field.getName() + " Value: " + value, Toast.LENGTH_SHORT).show();
                    //Obtener los pasos y guardarlo en el texrView
                    cifraPasos =  findViewById(R.id.cifra_pasos);
                    cifraPasos.setText(String.valueOf(value));

                    //Firebase
                    //Guardar el valor de value en firebase
                    Map<String, Object> pasos = new HashMap<>();
                    pasos.put("pasos", value.asInt());

                    //DocumentReference dr = db.collection("pasos").document(documentID);
                    dr = null;
                    //Comprobar cada vez que se concecte un usuario a la aplicacion
                    //Si el usuario no se ha conectado nunca se le genera un ID nuevo
                    if ("null".equals(documentID)){
                        db.collection("pasos")
                                .add(pasos)
                                .addOnSuccessListener(new OnSuccessListener<DocumentReference>() {
                                    @Override
                                    public void onSuccess(DocumentReference documentReference) {
                                        Log.d(LOG_TAG_FB, "DocumentSnapshot added with ID: " + documentReference.getId());
                                        SharedPreferences myPreferences = PreferenceManager.getDefaultSharedPreferences(MainActivity.this);
                                        SharedPreferences.Editor myEditor = myPreferences.edit();
                                        myEditor.putString("documentID", documentReference.getId());
                                        myEditor.commit();
                                        documentID = documentReference.getId();
                                    }
                                })
                                .addOnFailureListener(new OnFailureListener() {
                                    @Override
                                    public void onFailure(@NonNull Exception e) {
                                        Log.w(LOG_TAG_FB, "Error adding document", e);
                                    }
                                });

                    }else {
                        //sino se reutiliza ese ID
                        db.collection("pasos").document(documentID)
                                .set(pasos)
                                .addOnSuccessListener(new OnSuccessListener<Void>() {
                                    @Override
                                    public void onSuccess(Void aVoid) {
                                        Log.d(LOG_TAG_FB, "DocumentSnapshot Updated " );
                                    }
                                })
                                .addOnFailureListener(new OnFailureListener() {
                                    @Override
                                    public void onFailure(@NonNull Exception e) {
                                        Log.w(LOG_TAG_FB, "Error Updating document", e);
                                    }
                                });
                    }
                    //Devuelve el numero de usuarios conecatado ordenados de menos a mayor
                    //Actualiza el ranking siempre y cuando se este utilizando la aplicacion
                    db.collection("pasos")
                            .orderBy("pasos")
                            .get()
                            .addOnCompleteListener(new OnCompleteListener<QuerySnapshot>() {
                                @Override
                                public void onComplete(@NonNull Task<QuerySnapshot> task) {
                                    if (task.isSuccessful()) {
                                        numUsuarios = task.getResult().size();
                                        pos = 0;
                                        for (QueryDocumentSnapshot document : task.getResult()) {
                                            Log.d(LOG_TAG_CONSULTA, "ID: " + document.getId());
                                            if (documentID.equals(document.getId())) {
                                                break;
                                            }
                                            pos++;
                                        }
                                        cifraRanquing = findViewById(R.id.cifra_ranquing);
                                        cifraRanquing.setText(numUsuarios-pos + "/" + numUsuarios);

                                    } else {
                                        Log.d(LOG_TAG_CONSULTA, "Error getting documents: ", task.getException());
                                    }
                                }
                            });

                }

            });



    }
    //Funcion para regrescar el ranquin Si la aplicacion esta sin utilizarse
    public void refrescarRanking(){

        db.collection("pasos")
                .orderBy("pasos")
                .get()
                .addOnCompleteListener(new OnCompleteListener<QuerySnapshot>() {
                    @Override
                    public void onComplete(@NonNull Task<QuerySnapshot> task) {
                        if (task.isSuccessful()) {
                            numUsuarios = task.getResult().size();
                            pos = 0;
                            for (QueryDocumentSnapshot document : task.getResult()) {
                                Log.d(LOG_TAG_CONSULTA, "ID: " + document.getId());
                                if (documentID.equals(document.getId())) {
                                    break;
                                }
                                pos++;
                            }
                            cifraRanquing = findViewById(R.id.cifra_ranquing);
                            cifraRanquing.setText(numUsuarios-pos + "/" + numUsuarios);

                        } else {
                            Log.d(LOG_TAG_CONSULTA, "Error getting documents: ", task.getException());
                        }
                    }
                });
    }

    @Override
    protected void onStart() {
        super.onStart();
        mApiClient.connect();

        //leer documetID guardado
        SharedPreferences myPreferences = PreferenceManager.getDefaultSharedPreferences(MainActivity.this);

        documentID  = myPreferences.getString("documentID", "null");

    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if( requestCode == REQUEST_OAUTH ) {
            authInProgress = false;
            if( resultCode == RESULT_OK ) {
                if( !mApiClient.isConnecting() && !mApiClient.isConnected() ) {
                    mApiClient.connect();
                }
            } else if( resultCode == RESULT_CANCELED ) {
                Log.e( "GoogleFit", "RESULT_CANCELED" );
            }
        } else {
            Log.e("GoogleFit", "requestCode NOT request_oauth");
        }
    }


    @Override
    protected void onStop() {
        super.onStop();

        Fitness.SensorsApi.remove( mApiClient, this )
                .setResultCallback(new ResultCallback<Status>() {
                    @Override
                    public void onResult(Status status) {
                        if (status.isSuccess()) {
                            mApiClient.disconnect();
                        }
                    }
                });
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(AUTH_PENDING, authInProgress);
    }

}
