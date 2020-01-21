package ru.proteye.facebook_deeplinks;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;

import androidx.annotation.NonNull;
import io.flutter.embedding.engine.plugins.FlutterPlugin;
import io.flutter.embedding.engine.plugins.activity.ActivityAware;
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding;
import io.flutter.plugin.common.BinaryMessenger;
import io.flutter.plugin.common.EventChannel;
import io.flutter.plugin.common.EventChannel.EventSink;
import io.flutter.plugin.common.EventChannel.StreamHandler;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.MethodChannel.Result;
import io.flutter.plugin.common.PluginRegistry;
import io.flutter.plugin.common.PluginRegistry.Registrar;
import com.facebook.FacebookSdk;
import com.facebook.applinks.AppLinkData;

/** FacebookDeeplinksPlugin */
public class FacebookDeeplinksPlugin implements FlutterPlugin, ActivityAware, MethodCallHandler, StreamHandler, PluginRegistry.NewIntentListener {
  private static final String MESSAGES_CHANNEL = "ru.proteye/facebook_deeplinks/channel";
  private static final String EVENTS_CHANNEL = "ru.proteye/facebook_deeplinks/events";

  private MethodChannel methodChannel;
  private EventChannel eventChannel;
  private EventSink events;
  private BroadcastReceiver linksReceiver;
  private ActivityPluginBinding activityPluginBinding;
  private Registrar registrar;
  private Context context;
  private String initialUrl;

  @Override
  public void onAttachedToEngine(@NonNull FlutterPluginBinding flutterPluginBinding) {
    setupChannels(flutterPluginBinding.getFlutterEngine().getDartExecutor(), flutterPluginBinding.getApplicationContext());
  }

  @Override
  public void onDetachedFromEngine(@NonNull FlutterPluginBinding binding) {
    methodChannel.setMethodCallHandler(null);
    eventChannel.setStreamHandler(null);
    methodChannel = null;
    eventChannel = null;
  }

  // This static function is optional and equivalent to onAttachedToEngine. It supports the old
  // pre-Flutter-1.12 Android projects. You are encouraged to continue supporting
  // plugin registration via this function while apps migrate to use the new Android APIs
  // post-flutter-1.12 via https://flutter.dev/go/android-project-migration.
  //
  // It is encouraged to share logic between onAttachedToEngine and registerWith to keep
  // them functionally equivalent. Only one of onAttachedToEngine or registerWith will be called
  // depending on the user's project. onAttachedToEngine or registerWith must both be defined
  // in the same class.
  public static void registerWith(Registrar registrar) {
    this.registrar = registrar;
    FacebookDeeplinksPlugin plugin = new FacebookDeeplinksPlugin();
    plugin.setupChannels(registrar.messenger(), registrar.context());
    registrar.addNewIntentListener(plugin);
  }

  @Override
  public void onAttachedToActivity(ActivityPluginBinding binding) {
    activityPluginBinding = binding;
    binding.addOnNewIntentListener(this);
  }

  @Override
  public void onDetachedFromActivityForConfigChanges() {}

  @Override
  public void onReattachedToActivityForConfigChanges(ActivityPluginBinding activityPluginBinding) {}

  @Override
  public void onDetachedFromActivity() {}

  private void setupChannels(BinaryMessenger messenger, Context context) {
    this.context = context;
    methodChannel = new MethodChannel(messenger, MESSAGES_CHANNEL);
    methodChannel.setMethodCallHandler(this);

    eventChannel = new EventChannel(messenger, EVENTS_CHANNEL);
    eventChannel.setStreamHandler(this);

    initFacebookAppLink();
  }

  private void initFacebookAppLink() {
    Intent intent = getIntent();
    if (intent != null && intent.getAction() == Intent.ACTION_VIEW) {
      initialUrl = intent.getDataString();
      handleLink(initialUrl);
    }

    FacebookSdk.setAutoInitEnabled(true);
    FacebookSdk.fullyInitialize();
    AppLinkData.fetchDeferredAppLinkData(context, 
      new AppLinkData.CompletionHandler() {
        @Override
        public void onDeferredAppLinkDataFetched(AppLinkData appLinkData) {
          if (appLinkData == null) {
            return;
          }
          initialUrl = appLinkData.getTargetUri().toString();
          handleLink(initialUrl);
        }
      }
    );
  }

  private Intent getIntent() {
    if (activityPluginBinding != null) {
      return activityPluginBinding.getActivity().getIntent();
    }

    if (registrar != null) {
      return registrar.activity().getIntent();
    }

    return null;
  }

  // private void initFacebookAppLink(@NonNull Result result) {
  //   final Result resultDelegate = result;
  //   final Handler mainHandler = new Handler(context.getMainLooper());

  //   FacebookSdk.setAutoInitEnabled(true);
  //   FacebookSdk.fullyInitialize();
  //   AppLinkData.fetchDeferredAppLinkData(context, 
  //     new AppLinkData.CompletionHandler() {
  //       @Override
  //       public void onDeferredAppLinkDataFetched(AppLinkData appLinkData) {
  //         if (appLinkData == null) {
  //           return;
  //         }

  //         url = appLinkData.getTargetUri().toString();
  //         Runnable myRunnable = new Runnable() {
  //           @Override
  //           public void run() {
  //             if (resultDelegate != null) {
  //               resultDelegate.success(url);
  //             }
  //           }
  //         };
  //         mainHandler.post(myRunnable);
  //       }
  //     }
  //   );
  // }

  @Override
  public void onMethodCall(@NonNull MethodCall call, @NonNull Result result) {
    if (call.method.equals("initialUrl")) {
      result(initialUrl);
    } else {
      result.notImplemented();
    }
  }

  @Override
  public boolean onNewIntent(Intent intent) {
    if (linksReceiver != null) {
      linksReceiver.onReceive(context, intent);
    }
    return false;
  }

  @Override
  public void onListen(Object args, final EventSink events) {
    this.events = events;
    linksReceiver = createChangeReceiver(events);
  }

  @Override
  public void onCancel(Object args) {
    events = null;
    linksReceiver = null;
  }

  private boolean handleLink(String link) {
    if (events != null && link != null) {
      events.success(link);
      return true;
    }
    return false;
  }

  private BroadcastReceiver createChangeReceiver(final EventSink events) {
    return new BroadcastReceiver() {
      @Override
      public void onReceive(Context context, Intent intent) {
        // NOTE: assuming intent.getAction() is Intent.ACTION_VIEW
        if (intent.getAction() != Intent.ACTION_VIEW) {
          return;
        }

        String dataString = intent.getDataString();

        if (dataString != null) {
          events.success(dataString);
        }
      }
    };
  }
}
