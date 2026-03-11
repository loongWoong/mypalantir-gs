/**
 * 示例脚本函数：toll 场景下的规则校验。
 * 约定：定义 execute(args) 函数，args 为 Java 传入的参数列表（List）。
 * 在 Nashorn 中可用 args.get(0)、args.size()；在 GraalJS 中可能为数组，可用 args[0]、args.length。
 * 返回 boolean / number / string 等，将回传给调用方。
 */
function execute(args) {
  if (!args) return false;
  var len = typeof args.size === 'function' ? args.size() : (args.length || 0);
  if (len === 0) return false;
  var first = typeof args.get === 'function' ? args.get(0) : args[0];
  if (typeof first === 'object' && first !== null) {
    return first.pass_id != null || first.id != null;
  }
  return Boolean(first);
}
