function run(gantry_transactions, split_details) {
  // 参数校验
  if (gantry_transactions == null || !Array.isArray(gantry_transactions) || split_details == null) return false;
  
  // 获取 split_details 中的 pro_split_time
  var split_time = split_details.pro_split_time;
  if (split_time == null) return false;
  
  // 统一转换 split_time 为毫秒时间戳
  var t = typeof split_time === 'string' 
    ? new Date(split_time).getTime() 
    : (split_time && split_time.getTime ? split_time.getTime() : Number(split_time));
  
  // 遍历交易记录
  for (var i = 0; i < gantry_transactions.length; i++) {
    var rt = gantry_transactions[i].receive_time;
    if (rt == null) continue;
    
    // 统一转换 receive_time 为毫秒时间戳
    var rtMs = typeof rt === 'string' 
      ? new Date(rt).getTime() 
      : (rt.getTime ? rt.getTime() : Number(rt));
    
    // 判断：split_time 是否晚于 receive_time（即 receive_time < split_time）
    if (rtMs < t) return true;
  }
  
  return false;
}