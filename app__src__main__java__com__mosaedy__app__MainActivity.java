package com.mosaedy.app;
import android.Manifest;import android.app.*;import android.content.*;import android.content.pm.PackageManager;import android.graphics.Color;import android.os.*;import android.speech.*;import android.speech.tts.TextToSpeech;import android.view.*;import android.widget.*;import org.json.*;import java.text.*;import java.util.*;import java.util.regex.*;
public class MainActivity extends Activity {
 LinearLayout root,items;EditText command,wake;TextToSpeech tts;Spinner voices;Calendar selected=Calendar.getInstance();boolean dateChosen=false;
 int pad=18;
 public void onCreate(Bundle b){super.onCreate(b);getWindow().setStatusBarColor(Color.rgb(24,55,99));if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},10);
 tts=new TextToSpeech(this,status->{if(status==TextToSpeech.SUCCESS)tts.setLanguage(new Locale("ar"));});render();}
 TextView text(String s,int size){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(Color.rgb(20,44,78));t.setPadding(6,12,6,12);return t;}
 Button button(String s,Runnable r){Button b=new Button(this);b.setText(s);b.setOnClickListener(v->r.run());return b;}
 void say(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();if(tts!=null)tts.speak(s,TextToSpeech.QUEUE_FLUSH,null,"reply");}
 void render(){ScrollView scroll=new ScrollView(this);root=new LinearLayout(this);root.setOrientation(1);root.setPadding(pad,pad,pad,pad);root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);root.setBackgroundColor(Color.WHITE);scroll.addView(root);setContentView(scroll);
 root.addView(text("مساعدي | مساعدك الشخصي",25));root.addView(text("سجّل تذكيرك بالكتابة أو بالصوت، أو اختار موعدًا يدويًا.",15));
 command=new EditText(this);command.setHint("مثال: فكرني بعد 5 دقايق أكلم أحمد");command.setSingleLine(false);root.addView(command);
 root.addView(button("إرسال الأمر",()->parse(command.getText().toString())));
 root.addView(button("🎙 اتكلم مع مساعدي",this::listen));
 root.addView(button("اختيار التاريخ والوقت يدويًا",this::chooseDate));
 root.addView(button("حفظ التذكير في الموعد المختار",()->{if(!dateChosen){say("اختار التاريخ والوقت الأول");return;}String s=command.getText().toString().trim();if(s.isEmpty()){say("اكتب عنوان التذكير");return;}save(s,selected.getTimeInMillis());}));
 root.addView(text("التذكيرات المحفوظة",21));items=new LinearLayout(this);items.setOrientation(1);root.addView(items);refresh();
 root.addView(text("إعدادات الصوت والنداء",21));root.addView(text("كلمة النداء (محفوظة للتطوير القادم؛ لا تعمل بالخلفية حاليًا)",13));wake=new EditText(this);wake.setText(getPreferences(0).getString("wake","يا مساعدي"));root.addView(wake);root.addView(button("حفظ كلمة النداء",()->{getPreferences(0).edit().putString("wake",wake.getText().toString()).apply();say("اتحفظت كلمة النداء");}));
 root.addView(text("الصوت الرجالي أو النسائي يعتمد على الأصوات المتاحة في محرك الهاتف؛ لا يمكن ضمان نوع الصوت.",13));root.addView(button("فتح إعدادات تحويل النص إلى كلام",()->{try{startActivity(new Intent("com.android.settings.TTS_SETTINGS"));}catch(Exception e){say("إعدادات الصوت غير متاحة على الجهاز");}}));
 root.addView(text("تنبيه: هذه النسخة لا تحتوي على محادثة ذكاء اصطناعي سحابية أو استماع دائم لكلمة النداء.",13));}
 void refresh(){items.removeAllViews();JSONArray a=ReminderStore.get(this);SimpleDateFormat f=new SimpleDateFormat("EEE d MMM yyyy - h:mm a",new Locale("ar"));for(int j=0;j<a.length();j++)try{JSONObject o=a.getJSONObject(j);items.addView(text("• "+o.getString("title")+"\n"+f.format(new Date(o.getLong("at"))),16));}catch(Exception ignored){}if(a.length()==0)items.addView(text("مفيش تذكيرات لسه",14));}
 void save(String s,long when){if(when<=System.currentTimeMillis()){say("الوقت لازم يكون في المستقبل");return;}ReminderStore.add(this,s,when);ReminderStore.restore(this);say("اتسجل التذكير");refresh();}
 void chooseDate(){Calendar now=Calendar.getInstance();DatePickerDialog d=new DatePickerDialog(this,(view,y,m,day)->{TimePickerDialog t=new TimePickerDialog(this,(v,h,min)->{selected=Calendar.getInstance();selected.set(y,m,day,h,min,0);selected.set(Calendar.MILLISECOND,0);dateChosen=true;say("اتحدد الموعد "+new SimpleDateFormat("d/M/yyyy h:mm a",new Locale("ar")).format(selected.getTime()));},now.get(Calendar.HOUR_OF_DAY),now.get(Calendar.MINUTE),false);t.show();},now.get(Calendar.YEAR),now.get(Calendar.MONTH),now.get(Calendar.DAY_OF_MONTH));d.getDatePicker().setMinDate(System.currentTimeMillis()-86400000L);d.show();}
 void listen(){if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},11);return;}Intent i=new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);i.putExtra(RecognizerIntent.EXTRA_LANGUAGE,"ar-EG");i.putExtra(RecognizerIntent.EXTRA_PROMPT,"قول التذكير");try{startActivityForResult(i,7);}catch(Exception e){say("التعرف على الكلام غير متوفر على الموبايل");}}
 protected void onActivityResult(int req,int res,Intent data){super.onActivityResult(req,res,data);if(req==7 && res==RESULT_OK && data!=null){ArrayList<String> a=data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);if(a!=null&&!a.isEmpty()){command.setText(a.get(0));parse(a.get(0));}}}
 void parse(String raw){String s=raw.trim();if(s.isEmpty()){say("قول أو اكتب طلبك");return;}if(s.contains("عندي ايه")||s.contains("مواعيدي")){say("عندك "+ReminderStore.get(this).length()+" تذكيرات محفوظة، هتلاقيها تحت");return;}
 String normalized=s.replace('٠','0').replace('١','1').replace('٢','2').replace('٣','3').replace('٤','4').replace('٥','5').replace('٦','6').replace('٧','7').replace('٨','8').replace('٩','9');
 Matcher m=Pattern.compile("بعد\\s+(\\d+)\\s*(دقايق|دقيقة|دقائق|ساعه|ساعة|ساعات|يوم|ايام|أيام)").matcher(normalized);
 if(m.find()){long n=Long.parseLong(m.group(1));String unit=m.group(2);long millis=unit.startsWith("د")?60000L:unit.startsWith("س")?3600000L:86400000L;save(s,System.currentTimeMillis()+n*millis);return;}
 say("فهمت طلبك، لكن محتاج تحدد الوقت بدقة. اضغط اختيار التاريخ والوقت، وبعدها حفظ التذكير.");}
 protected void onDestroy(){if(tts!=null){tts.stop();tts.shutdown();}super.onDestroy();}
}
