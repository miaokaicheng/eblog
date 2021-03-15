/**

 @Name: 好友板块

 */

layui.define(function(exports){
  var fly = layui.fly;

  //好友管理
  var gather = {
    //加好友,删除好友
    friend: function(div){
      var othis = $(this), type = othis.data('type');
      fly.json('/user/friend/'+ type +'/', {
        fid: div.data('user')
      }, function(res){
        if(type === 'add'){
          othis.data('type', 'remove').html('删除好友');
        } else if(type === 'remove'){
          othis.data('type', 'add').html('加为好友');
        }
      });
    }
  };

  $('body').on('click', '.fly-imActive', function(){
    var othis = $(this), type = othis.attr('type');
    gather[type] && gather[type].call(this, othis.parent());
  });

  //异步渲染
  var asyncRender = function(){
    var div = $('.fly-sns');
    //查询是否是好友
    if(div[0] && layui.cache.user.uid !== -1){
      fly.json('/user/friend/find/', {
        friendId: div.data('user')
      }, function(res){
        div.append('<a class="layui-btn layui-btn-primary fly-imActive" type="friend" data-type="'+ (res.data.friend ? 'remove' : 'add') +'">'+ (res.data.friend ? '删除好友' : '加为好友') +'</a>');
      });
    }
  }();
  exports('friend', null);
});