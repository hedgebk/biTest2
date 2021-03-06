var periodicalUpdater = null;

function theStatus(text) {
  var ctrl = $('theStatus');
  ctrl.innerHTML = text;
}

function ajaxCommand(command, callback) {
  new Ajax.Request('/tst', {
    method:'get',
    parameters:{'command':command},
    onSuccess:function (transport) {
      var json = transport.responseText.evalJSON();
      var status = json.status;
      theStatus("Command '" + command + "' status '" + status + "'");
      if (callback != null) {
        callback(status);
      }
    },
    onFailure:function () { theStatus("Command '" + command + "' failed."); }
  });
}

function onStartStatus(status) {
  if (status == "need config" ) {
    window.location = "config.jsp";
  }
}

function onLoad() {
  draw();

  $('start').observe('click', function() { ajaxCommand('start', onStartStatus); });
  $('stop').observe('click', function() { ajaxCommand('stop'); });
  $('periodic').observe('click', function() { pool(); });
  $('periodicX').observe('click', function() { stopPool(); });

  function pool() {
    theStatus('pool');
    if (periodicalUpdater == null) {
      periodicalUpdater = new Ajax.PeriodicalUpdater('products', '/tst', {
        method:'get',
        parameters:{command:'state'},
        onSuccess:function (transport) {
          var json = transport.responseText.evalJSON();
          drawStatus(json);
        },
        onFailure:function () { alert('Something went wrong...'); },
        frequency:3
      });
    }
  }

  function stopPool() {
    theStatus('stopPool');
    if (periodicalUpdater != null) {
      periodicalUpdater.stop();
      periodicalUpdater = null;
    }
  }

//  new Ajax.Request('/progress.jsp', {
//    method:'get',
//    parameters:{company:'example', limit:12},
//    onSuccess:function (transport) {
//      var json = transport.responseText.evalJSON();
//      var counter = json.counter;
//      alert('OK. json: ' + json + "\n name=" + json.name + "\n occupation=" + json.occupation + "\n counter=" + counter);
//      pool();
//    },
//    onFailure:function () {
//      alert('Something went wrong...');
//    }
//  });
}


function draw() {
  var c = document.getElementById("123");
  var ctx = c.getContext("2d");
  ctx.fillStyle = "#000000";
  // ctx.fillStyle = "rgb(200,0,0)";
  // ctx.fillStyle = "rgba(0, 0, 200, 0.5)";
  ctx.fillRect(1, 1, 990, 490);

//  ctx.beginPath();
//  ctx.rect(188, 50, 200, 100);
//  ctx.fillStyle = 'yellow';
//  ctx.fill();
//  ctx.lineWidth = 7;
//  ctx.strokeStyle = 'black';
//  ctx.stroke();
//
//  ctx.beginPath();
//  ctx.moveTo(170, 80);
//  ctx.bezierCurveTo(130, 100, 130, 150, 230, 150);
//  ctx.bezierCurveTo(250, 180, 320, 180, 340, 150);
//  ctx.bezierCurveTo(420, 150, 420, 120, 390, 100);
//  ctx.bezierCurveTo(430, 40, 370, 30, 340, 50);
//  ctx.bezierCurveTo(320, 5, 250, 20, 250, 50);
//  ctx.bezierCurveTo(200, 5, 150, 20, 170, 80);
//  ctx.closePath();
//  ctx.lineWidth = 5;
//  ctx.strokeStyle = 'blue';
//  ctx.stroke();
//
//  ctx.lineWidth = 5;
//  ctx.lineCap = 'round';
//  ctx.save();
//  ctx.beginPath();
//  ctx.arc(200, 250, 30, 0, Math.PI * 0.7, true); // The radius is 30 pixels.
//  ctx.fill();
//
//  ctx.beginPath();
//  ctx.strokeStyle = '#c00';
//  ctx.lineWidth = 3;
//  ctx.arc(100, 150, 20, 0, Math.PI, false);
//  ctx.stroke();
//
//  ctx.restore();
//  ctx.beginPath();
//  ctx.moveTo(100, 80); // move to neck
//  ctx.lineTo(100, 180); // body
//  ctx.lineTo(75, 250); // left leg
//  ctx.moveTo(100, 180); // move to hips
//  ctx.lineTo(125, 250); // right leg
//  ctx.moveTo(100, 90); // move to shoulders
//  ctx.lineTo(75, 140); // left argm
//  ctx.moveTo(100, 90); // back to shoulders
//  ctx.lineTo(125, 140); // right leg
//  // ctx.closePath();
//  ctx.stroke();
//
//  var my_gradient = ctx.createLinearGradient(300, 225, 300, 500);
//  my_gradient.addColorStop(0, "black");
//  my_gradient.addColorStop(1, "white");
//  ctx.fillStyle = my_gradient;
//  ctx.fillRect(300, 225, 200, 150);

  ctx.font = "bold 13px sans-serif";
  setInterval(function () {
    ctx.clearRect(10, 380, 400, 18);
    ctx.fillStyle = "blue";
    ctx.fillText("?" + new Date(), 14, 394);
  }, 1000);
}

function drawExchStatus(ctx, aExch, y) {
  var ret = y;
  ctx.fillText(": " + aExch.name,  26, ret);
  ctx.fillText( aExch.state, 95, ret);
  ret += 18;
  ctx.fillText( aExch.ord,  46, ret);
  ret += 18;
  return ret;
}

function drawForkStatus(ctx, aFork, y) {
  var ret = y;
  ctx.fillText(": " + aFork.state + " " + aFork.live, 20, y);
  ret += 18;

  var e1 = aFork.e1;
  ret = drawExchStatus(ctx, e1, ret);

  var e2 = aFork.e2;
  ret = drawExchStatus(ctx, e2, ret);

  return ret;
}

function drawStatus(json) {
//  alert("json="+json);
  var c = document.getElementById("123");
  var ctx = c.getContext("2d");

  ctx.font = "bold 13px sans-serif";
  ctx.clearRect(10, 10, 700, 150);
  ctx.fillStyle = "red";

//  alert("json.status="+json.status);

  // { 'status': 'ok', 'e1' : 'unknown', 'e2' : 'unknown' }
  ctx.fillText("status: " + json.status + " " + json.time, 14, 24);
//  alert("json.forks="+json.forks);
  var allForks = json.forks;
  var y = 24 + 18;
  for (var i = 0; i < allForks.length; i++) {
    var aFork = allForks[i];
//    alert("fork=" + aFork);
    y = drawForkStatus(ctx, aFork, y);
  }
}
