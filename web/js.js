function draw() {
    var c=document.getElementById("123");
    var ctx=c.getContext("2d");
    ctx.fillStyle="#000000";
    // ctx.fillStyle = "rgb(200,0,0)";
    // ctx.fillStyle = "rgba(0, 0, 200, 0.5)";
    ctx.fillRect(1,1,480,480);
//         ctx.fillStyle="#FFFFFF";
//    ...jsp code to generate the map pixels...

    ctx.beginPath();
    ctx.rect(188, 50, 200, 100);
    ctx.fillStyle = 'yellow';
    ctx.fill();
    ctx.lineWidth = 7;
    ctx.strokeStyle = 'black';
    ctx.stroke();

    // begin custom shape
    ctx.beginPath();
    ctx.moveTo(170, 80);
    ctx.bezierCurveTo(130, 100, 130, 150, 230, 150);
    ctx.bezierCurveTo(250, 180, 320, 180, 340, 150);
    ctx.bezierCurveTo(420, 150, 420, 120, 390, 100);
    ctx.bezierCurveTo(430, 40, 370, 30, 340, 50);
    ctx.bezierCurveTo(320, 5, 250, 20, 250, 50);
    ctx.bezierCurveTo(200, 5, 150, 20, 170, 80);
    ctx.closePath();
    ctx.lineWidth = 5;
    ctx.strokeStyle = 'blue';
    ctx.stroke();

    ctx.lineWidth = 5;
    ctx.lineCap = 'round';
    ctx.save();
    ctx.beginPath();
    ctx.arc(200, 250, 30, 0, Math.PI*0.7, true); // The radius is 30 pixels.
    ctx.fill();

    // smile path in red
    ctx.beginPath();
    ctx.strokeStyle = '#c00';
    ctx.lineWidth = 3;
    ctx.arc(100, 150, 20, 0, Math.PI, false);
    ctx.stroke();

    ctx.restore();
    ctx.beginPath();
    ctx.moveTo(100, 80); // move to neck
    ctx.lineTo(100, 180); // body
    ctx.lineTo(75, 250); // left leg
    ctx.moveTo(100, 180); // move to hips
    ctx.lineTo(125, 250); // right leg
    ctx.moveTo(100, 90); // move to shoulders
    ctx.lineTo(75, 140); // left argm
    ctx.moveTo(100, 90); // back to shoulders
    ctx.lineTo(125, 140); // right leg
    // ctx.closePath();
    ctx.stroke();

    var my_gradient = ctx.createLinearGradient(0, 0, 300, 0);
    my_gradient.addColorStop(0, "black");
    my_gradient.addColorStop(1, "white");
    ctx.fillStyle = my_gradient;
    ctx.fillRect(0, 0, 300, 225);

    ctx.font = "bold 13px sans-serif";
    setInterval(function () {
        ctx.clearRect(10, 400, 400, 18);
        //ctx.strokeStyle = 'blue';
        ctx.fillStyle="blue"; //"#000000";
        ctx.fillText(new Date(), 14, 414);
        //ctx.commit();
    }, 1000);
}
