/* Mosaedy 3.3.1 local conversation context & edit commands. No cloud AI. */
(function(root){'use strict';
const normalize=s=>String(s||'').replace(/[\u064B-\u065F\u0670ـ]/g,'').replace(/[٠-٩]/g,c=>String('٠١٢٣٤٥٦٧٨٩'.indexOf(c))).replace(/\s+/g,' ').trim();
function classify(raw){let t=normalize(raw);
 if(/^(?:what(?:'s|’s| is) on my schedule|show my reminders|list reminders|what do i have|what is on my schedule|remind me|please remind me|set a reminder|add a reminder)\b/i.test(t))return /what|show|list/i.test(t)?'list':'new';
 if(/^(?:undo|undo that|restore the reminder|تراجع|رجعها|ارجعها|الغاء الحذف|الغي الحذف)$/.test(t))return 'undo';
 if(/^(?:cancel|cancel that|delete that reminder|الغيه|إلغيه|الغيها|الغاء|إلغاء|امسحه|امسحها|احذفه|احذفها|شيله|شيلها|كنسل|كانسل|مش عايزه|مش عايزاها|خلاص الغيه)(?:\s*بقى)?$/i.test(t))return 'cancel';
 if(/^(?:edit|change it|change that|modify it|move it|postpone|push it|snooze|make it|change the time|عدل|عدّل|تعديل|عدلها|عدله|أجلها|اجلها|أخرها|اخرها)/i.test(t))return 'edit';
 if(/(?:عندي ايه|عندي إيه|مواعيدي|تذكيراتي|جدولي|ايه اللي ورايا)/.test(t))return 'list';
 if(/^(?:لا\s*)?(?:الغيه|إلغيه|الغيها|الغاء|إلغاء|امسحه|امسحها|احذفه|احذفها|شيله|شيلها|كنسل|كانسل|مش عايزه|مش عايزاها|خلاص الغيه)(?:\s*بقى)?$/.test(t))return 'cancel';
 if(/^(?:لا\s*)?(?:خليها|خليه|خلّيها|خليه|بدلها|غيرها|غيره|عدله|عدلها|عدّل|عدل|تعديل|اجلها|أجلها|أخرها|اخرها|قدمها|قدّمها|بصوت|بإشعار|بالارم|بألارم|قبلها|بعدها)/.test(t))return 'edit';
 if(/(?:فكرني|فكريني|ذكرني|ذكريني|نبهني|نبّهني|سجلي|سجل|ضيف|اضف|أضف|حطلي|ذكر|موعد|تذكير|كمان\s+\S+\s+(?:دقيق|ساع|ثان)|بعد\s+\S+\s+(?:دقيق|ساع|ثان))/.test(t))return 'new';
 if(/\b(?:tomorrow|today|in\s+\d+\s+(?:seconds?|minutes?|hours?|days?)|after\s+\d+\s+(?:seconds?|minutes?|hours?|days?)|at\s+\d{1,2}(?::\d{2})?\s*(?:am|pm))\b/i.test(t)||/(?:بكرة|النهارده|بعد\s+\S+\s+(?:دقيق|ساع|ثان)|الساعة\s*\d)/.test(t))return 'new';
 return 'other';}
function edit(raw,old,nowMs){let t=normalize(raw),now=Number(nowMs||Date.now());let updates={};
 let enDelta=/\b(?:move it|postpone|push it|snooze|make it later)\s*(?:it|the reminder)?\s*(?:by|for)?\s*(\d+)\s*(seconds?|secs?|minutes?|mins?|hours?)\b/i.exec(t);
 if(enDelta){let n=Number(enDelta[1]),u=enDelta[2].toLowerCase(),ms=/sec/.test(u)?1000:/min/.test(u)?60000:3600000;updates.dueAt=Number(old.dueAt)+n*ms;}
 if(/(?:إشعار|اشعار|نوتيفيكيشن|notification)/i.test(t))updates.mode='notification';else if(/(?:ألارم|الارم|منبه|رنين|\balarm\b)/i.test(t))updates.mode='alarm';
 let deltaM=t.match(/(اخرها|أخرها|اجلها|أجلها|بعدها|قدمها|قدّمها|قبلها)\s*(?:ب|بعد)?\s*(\d+|نص|ربع)\s*(ثانيه|ثانية|ثواني|دقيقه|دقيقة|دقائق|دقايق|ساعة|ساعه|ساعات)/);
 if(deltaM){let n=deltaM[2]==='نص'?.5:deltaM[2]==='ربع'?.25:Number(deltaM[2]);let ms=/ثان/.test(deltaM[3])?1000:/دق/.test(deltaM[3])?60000:3600000;let sign=/قدم|قدّم|قبل/.test(deltaM[1])?-1:1;updates.dueAt=Number(old.dueAt)+sign*n*ms;}
 else if(/(?:الساعة|الساعه|بكرة|النهارده|اليوم|بعد|كمان|السبت|الأحد|الاحد|الاثنين|الثلاثاء|الأربعاء|الخميس|الجمعة)/.test(t)){
  let tmp=t.replace(/^(?:لا\s*)?(?:خليها|خليه|خلّيها|بدلها|غيرها|غيره|عدله|عدلها|عدّل|عدل|تعديل|اجلها|أجلها)\s*/,'').trim();
  let absoluteDay=/(?:بكرة|النهارده|اليوم|بعد بكرة|السبت|الأحد|الاثنين|الثلاثاء|الأربعاء|الخميس|الجمعة)/.test(tmp);
  let relative=/(?:بعد|كمان)\s*(\d+|نص|ربع|دقيقة|ثانية|ساعتين)/.test(tmp);
  let parsed=root.MosaedyParser.parse('فكرني '+tmp+' '+old.title,now,{defaultMode:old.mode});
  if(parsed.ok){updates.dueAt=parsed.dueAt;
    if(!absoluteDay&&!relative&&/(?:الساعة|الساعه)/.test(tmp)){
      let oldDate=new Date(old.dueAt),current=new Date(parsed.dueAt);
      oldDate.setHours(current.getHours(),current.getMinutes(),current.getSeconds(),0);
      if(oldDate.getTime()>now)updates.dueAt=oldDate.getTime();
    }
  }else if(!Object.keys(updates).length)return {ok:false,message:parsed.message||'قولّي الموعد الجديد بالتحديد.'};
 }
 if(!Object.keys(updates).length)return {ok:false,message:'قولّي التعديل المطلوب: «خليها بعد ساعة»، «قدّمها ربع ساعة»، أو «خليها بإشعار». '};
 if(updates.dueAt!==undefined&&updates.dueAt<=now)return {ok:false,message:'الوقت الجديد فات، اختار موعد جاي.'};
 return {ok:true,updates};
}
const api={normalize,classify,edit}; if(typeof module!=='undefined'&&module.exports)module.exports=api;root.MosaedyCommands=api;
})(typeof window!=='undefined'?window:globalThis);
