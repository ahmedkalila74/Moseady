/* Mosaedy 3.2 local Egyptian Arabic reminder parser. No AI or network calls. */
(function (root) {
  'use strict';
  const AR = '٠١٢٣٤٥٦٧٨٩', FA = '۰۱۲۳۴۵۶۷۸۹';
  const words = { 'صفر':0,'واحد':1,'واحدة':1,'واحده':1,'اتنين':2,'اثنين':2,'اثنان':2,'ثلاث':3,'تلات':3,'تلاتة':3,'ثلاثة':3,'اربعة':4,'أربعة':4,'خمسة':5,'خمس':5,'ستة':6,'سته':6,'سبعة':7,'سبعه':7,'ثمانية':8,'تمانية':8,'تسعة':9,'تسعه':9,'عشرة':10,'عشر':10,'خمستاشر':15,'ربع':0.25,'نص':0.5,'نصف':0.5 };
  function digits(text){return String(text).replace(/[٠-٩۰-۹]/g,c=>String(AR.includes(c)?AR.indexOf(c):FA.indexOf(c)));}
  function normalized(input){return digits(input).replace(/[\u064B-\u065F\u0670ـ]/g,'').replace(/\s+/g,' ').trim();}
  function val(s){if(s==null)return null; s=digits(s).trim();if(/^\d+(?:[.,]\d+)?$/.test(s))return Number(s.replace(',','.'));return words[s]===undefined?null:words[s];}
  function formatTime(ms){return new Date(ms).toLocaleString('ar-EG',{weekday:'long',day:'numeric',month:'long',hour:'numeric',minute:'2-digit',second:'2-digit'});}
  const weekdays=['الأحد','الاثنين','الثلاثاء','الأربعاء','الخميس','الجمعة','السبت'];
  const enWeekdays=['Sunday','Monday','Tuesday','Wednesday','Thursday','Friday','Saturday'];
  const enNumbers={'a':1,'an':1,'one':1,'two':2,'three':3,'four':4,'five':5,'six':6,'seven':7,'eight':8,'nine':9,'ten':10,'fifteen':15,'twenty':20,'thirty':30};
  const wordNum='(?:\\d{1,5}|صفر|واحده?|واحدة|اتنين|اثنين|ثلاثة|تلاتة|تلات|اربعة|أربعة|خمسة|خمس|ستة|سته|سبعة|سبعه|ثمانية|تمانية|تسعة|تسعه|عشرة|عشر|خمستاشر|ربع|نص|نصف|one|a|an|two|three|four|five|six|seven|eight|nine|ten|fifteen|twenty|thirty)';
  const unit='(?:ثواني|ثوان|ثانية|ثانيه|ثوانى|دقايق|دقائق|دقيقة|دقيقه|ساعة|ساعه|ساعات|ساعتين|يوم|يومين|ايام|أيام|اسبوع|أسبوع|اسابيع|أسابيع)';
  const relativeRe = new RegExp('(?:بعد|كمان|خلال|من دلوقتي بعد)\\s+('+wordNum+')\\s*('+unit+')','i');
  function unitMs(u){return /ثان/.test(u)?1000:/دق/.test(u)?60000:/ساع/.test(u)?3600000:/يوم|ايام|أيام/.test(u)?86400000:604800000;}
  function removeSpan(s,match){return match?s.slice(0,match.index)+' '+s.slice(match.index+match[0].length):s;}
  function parse(input,nowMs,options={}){
    let original=String(input||'').trim(), text=normalized(input), now=new Date(nowMs==null?Date.now():nowMs);
    if(!text)return {ok:false,missing:'title',message:'قولّي محتاج أفكّرك بإيه.'};
    let due=null, rest=text, matchedTime=false, dayExplicit=false, dayOffset=0, meridiem=false;
    let repeat='none';
    if(/\b(every day|daily)\b|كل يوم/.test(text)) repeat='daily';
    else if(/\b(every week|weekly)\b|كل اسبوع|كل أسبوع/.test(text)) repeat='weekly';
    else if(/\b(every month|monthly)\b|كل شهر/.test(text)) repeat='monthly';
    let relative=relativeRe.exec(text);
    if(relative){let n=val(relative[1]);if(Number.isFinite(n)&&n>=0){due=now.getTime()+Math.max(1000,Math.round(n*unitMs(relative[2])));rest=removeSpan(rest,relative);matchedTime=true;}}
    if(due===null){
      const enRel=/\b(?:in|after)\s+(a|an|one|two|three|four|five|six|seven|eight|nine|ten|fifteen|twenty|thirty|\d{1,5})\s*(seconds?|secs?|minutes?|mins?|hours?|days?|weeks?)\b/i.exec(text);
      if(enRel){let n=/^\d+$/.test(enRel[1])?Number(enRel[1]):enNumbers[enRel[1].toLowerCase()];let u=enRel[2].toLowerCase();let ms=/sec/.test(u)?1000:/min/.test(u)?60000:/hour/.test(u)?3600000:/day/.test(u)?86400000:604800000;due=now.getTime()+Math.max(1000,Math.round(n*ms));rest=removeSpan(rest,enRel);matchedTime=true;}
      else {const enShort=/\b(?:in|after)\s+(a minute|one minute|a second|one second|an hour|one hour|half an hour|half hour|a day|one day)\b/i.exec(text);if(enShort){let v=enShort[1].toLowerCase();let ms=/second/.test(v)?1000:/minute/.test(v)?60000:/hour/.test(v)?3600000:86400000;if(/half/.test(v))ms/=2;due=now.getTime()+ms;rest=removeSpan(rest,enShort);matchedTime=true;}}
    }
    if(due===null){
      let shorthand=/(?:بعد|كمان)\s+(ساعتين|يومين|نص\s+ساعة|نصف\s+ساعة|ربع\s+ساعة|ساعة\s+ونص|ساعه\s+ونص|نص\s+دقيقة|ساعة|دقيقة|ثانية)/i.exec(text);
      if(shorthand){let s=shorthand[1],delta=/ساعتين/.test(s)?7200000:/يومين/.test(s)?172800000:/ربع/.test(s)?900000:/ساعة\s+ونص|ساعه\s+ونص/.test(s)?5400000:/نص|نصف/.test(s)?(/دقيقة/.test(s)?30000:1800000):/ثانية/.test(s)?1000:/ساعة/.test(s)?3600000:60000;due=now.getTime()+delta;rest=removeSpan(rest,shorthand);matchedTime=true;}
    }
    if(due===null && /(?:دلوقتي|حالاً|حالا|الان|الآن)/.test(text) && !/(?:الساعة|الساعه)/.test(text)){
      due=now.getTime()+3000;rest=rest.replace(/(?:دلوقتي|حالاً|حالا|الان|الآن)/g,' ');matchedTime=true;
    }
    if(due===null){
      let d=new Date(now.getTime());d.setMilliseconds(0);d.setSeconds(0);
      if(/\bday after tomorrow\b/i.test(text)){dayOffset=2;dayExplicit=true;rest=rest.replace(/\bday after tomorrow\b/i,' ');}
      else if(/\btomorrow\b|\bthe day after\b/i.test(text)){dayOffset=1;dayExplicit=true;rest=rest.replace(/\btomorrow\b|\bthe day after\b/i,' ');}
      else if(/\btoday\b/i.test(text)){dayOffset=0;dayExplicit=true;rest=rest.replace(/\btoday\b/i,' ');}
      else if(/بعد\s*بك(?:رة|ره)/.test(text)){dayOffset=2;dayExplicit=true;rest=rest.replace(/بعد\s*بك(?:رة|ره)/,' ');}
      else if(/بك(?:رة|ره)|غدا|غداً|غد/.test(text)){dayOffset=1;dayExplicit=true;rest=rest.replace(/بك(?:رة|ره)|غداً?|غدا/,' ');}
      else if(/النهارده|النهاردة|اليوم|النهاردا/.test(text)){dayOffset=0;dayExplicit=true;rest=rest.replace(/النهارده|النهاردة|النهاردا|اليوم/,' ');}
      else{
        let i=weekdays.findIndex(w=>text.includes(w));if(i>=0){dayOffset=(i-d.getDay()+7)%7;dayExplicit=true;rest=rest.replace(weekdays[i],' ');}
        else {let j=enWeekdays.findIndex(w=>new RegExp('\\b'+w+'\\b','i').test(text));if(j>=0){dayOffset=(j-d.getDay()+7)%7;dayExplicit=true;rest=rest.replace(new RegExp('\\b'+enWeekdays[j]+'\\b','i'),' ');}}
      }
      d.setDate(d.getDate()+dayOffset);
      // Explicit hour with optional minutes and seconds: "الساعة 6:30:15", "الساعة 6 ونص", "6:00".
      const clock=/(?:الساعة|الساعه|الساعه\s+|الساعة\s+)?\s*(\d{1,2})\s*(?::|٫|\.)\s*(\d{1,2})(?:\s*[:٫.]\s*(\d{1,2}))?(?=\s|$|[,،])/i;
      const named=/(?:الساعة|الساعه|الساعه\s+|الساعه\s+|ساعه|ساعة)\s*(\d{1,2}|اتنين|واحد|تلاتة|تلات|اربعة|أربعة|خمسة|ستة|سبعة|تمانية|تسعة|عشرة|حداشر|اتناشر)(?:\s*(?:و|والـ?)\s*(نص|نصف|ربع|تلت|\d{1,2}\s*دقيقة))?/i;
      let m=clock.exec(text), hour=null,minute=0,second=0;
      if(m){hour=Number(m[1]);minute=Number(m[2]);second=Number(m[3]||0);rest=rest.replace(m[0],' ');matchedTime=true;}
      else{m=named.exec(text);if(m){let h=m[1];hour=/^\d+$/.test(h)?Number(h):({'واحد':1,'اتنين':2,'تلات':3,'تلاتة':3,'اربعة':4,'أربعة':4,'خمسة':5,'ستة':6,'سبعة':7,'تمانية':8,'تسعة':9,'عشرة':10,'حداشر':11,'اتناشر':12}[h]);minute=m[2]?/نص|نصف/.test(m[2])?30:/ربع/.test(m[2])?15:/تلت/.test(m[2])?20:parseInt(m[2],10):0;rest=rest.replace(m[0],' ');matchedTime=true;}}
      if(hour===null){
        let en=/\b(?:at\s*)?(\d{1,2})(?::(\d{1,2}))?(?::(\d{1,2}))?\s*(am|pm)\b/i.exec(text);
        if(en){hour=Number(en[1]);minute=Number(en[2]||0);second=Number(en[3]||0);rest=rest.replace(en[0],' ');matchedTime=true;const tag=en[4].toLowerCase();if(tag==='pm'&&hour<12)hour+=12;if(tag==='am'&&hour===12)hour=0;meridiem=true;}
      }
      if(hour===null){
        // Bare hour after an explicit day or meridiem, like "بكرة 8 الصبح".
        m=/(?:^|\s)(\d{1,2})(?=\s*(?:الصبح|صباحا|صباحًا|مساء|المسا|بالليل|العصر|الضهر|الظهر|م\b|ص\b))/i.exec(text);
        if(m){hour=Number(m[1]);rest=rest.replace(m[0],' ');matchedTime=true;}
      }
      if(hour!==null){
        if(hour>23||minute>59||second>59)return {ok:false,missing:'invalid_time',message:'الساعة أو الدقايق أو الثواني مش صحيحة، راجعهم.'};
        const pm=/مساء|المسا|بالليل|ليل|العصر|المغرب|الضهر|الظهر|\sم(?:\s|$)|\bpm\b/i.test(text);
        const am=/الصبح|صباح|الفجر|\sص(?:\s|$)|\bam\b/i.test(text);
        meridiem=pm||am;
        if(pm && hour<12)hour+=12;
        if(am && hour===12)hour=0;
        d.setHours(hour,minute,second,0);
        if(!meridiem&&hour<=12&&(!dayExplicit || /النهارده|النهاردة|النهاردا|اليوم/.test(text))){ // Choose closest future 6/18; explicit today never rolls to tomorrow.
          let candidates=[hour===12?12:hour, hour===12?0:hour+12].map(h=>{let c=new Date(d);c.setHours(h,minute,second,0);if(!dayExplicit && c.getTime()<=now.getTime())c.setDate(c.getDate()+1);return c;});
          const future=candidates.filter(c=>c.getTime()>now.getTime());
          if(future.length)d=new Date(Math.min(...future.map(c=>c.getTime())));
          else return {ok:false,missing:'past',message:'الوقت ده فات النهارده. حدّد بكرة أو وقت تاني.'};
        } else if(!dayExplicit && d.getTime()<=now.getTime())d.setDate(d.getDate()+1);
        else if(dayExplicit && d.getTime()<=now.getTime()){
          // If weekday today, interpret next week; explicit "today" must not silently roll.
          if(!/النهارده|النهاردة|النهاردا|اليوم/.test(text) && dayOffset===0 && weekdays.some(w=>text.includes(w)))d.setDate(d.getDate()+7);
          else return {ok:false,missing:'past',message:'الوقت ده فات بالفعل. قولّي معاد في المستقبل أو استخدم «بكرة».'};
        }
        due=d.getTime();
      }
    }
    let mode=/(?:بإشعار|باشعار|نوتيفيكيشن|notification|إشعار عادي|quiet notification)/i.test(text)?'notification':/(?:بألارم|بالارم|مع رنين|بصوت منبه|رنين منبه|خليه منبه|خليها منبه|منبه|with alarm|alarm)/i.test(text)?'alarm':(options.defaultMode==='notification'?'notification':'alarm');
    rest=rest.replace(/(?:بألارم|بالارم|مع رنين|بصوت منبه|رنين منبه|خليه منبه|خليها منبه|بإشعار|باشعار|نوتيفيكيشن|notification)/gi,' ');
    rest=rest.replace(/(?:الصبح|صباحا|صباحًا|صباح|مساء|المسا|بالليل|بليل|العصر|المغرب|الضهر|الظهر|ليلا|ليلًا|\b(?:am|pm)\b|\b(?:every day|daily|every week|weekly|every month|monthly)\b)/gi,' ');
    rest=rest.replace(/^(?:يا\s+مساعدي\s*)?(?:فكرني|فكريني|ذكرني|ذكريني|نبّهني|نبهني|اعمل\s*لي|اعمللي|اعمل|سجل\s*لي|سجلي|سجل|حط\s*لي|حطلي|ضيف\s*لي|ضيف|اضف|أضف|عايز|محتاج|خلي|خليها|خلّيها|لا\s+خليها)\s*/i,' ')
      .replace(/^(?:ب|بـ)?(?:تذكير|موعد|ان|إني|اني|إنّي|انّي)\s+/i,' ')
      .replace(/^(?:لي|إن|إني|اني|ان|please remind me|remind me|remind|please)\s+/i,' ')
      .replace(/^\s*to\s+/i,' ')
      .replace(/^\s*to\s+/i,' ')
      .replace(/[،,؛:]+/g,' ').replace(/\s+/g,' ').replace(/^\s*[-،]+|[-،]+\s*$/g,'').trim();
    if(due===null)return {ok:false,missing:'time',title:rest,message:'فهمت المهمة، بس محتاج أعرف إمتى: مثلاً «بعد 30 ثانية» أو «النهارده الساعة 6 مساءً».'};
    if(!Number.isFinite(due)||due<=now.getTime())return {ok:false,missing:'past',message:'قولّي موعد لسه مجاش.'};
    if(!rest || rest.length<2)return {ok:false,missing:'title',dueAt:due,mode,message:'تمام، إمتى واضح. بس هفكّرك بإيه؟'};
    return {ok:true,title:rest.slice(0,160),notes:original,dueAt:due,mode,repeat};
  }
  const api={parse,normalized,digits,formatTime};
  if(typeof module!=='undefined'&&module.exports)module.exports=api;
  root.MosaedyParser=api;
})(typeof window!=='undefined'?window:globalThis);
