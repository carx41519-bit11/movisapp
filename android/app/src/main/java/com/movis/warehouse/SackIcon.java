package com.movis.warehouse;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

/** Neutral product illustration; brand identity always comes from the text. */
final class SackIcon extends View {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path sack=new Path();
    SackIcon(Context context){super(context);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);sack.moveTo(10,5);sack.lineTo(30,5);sack.lineTo(28,12);sack.cubicTo(33,21,36,32,32,39);sack.lineTo(8,39);sack.cubicTo(4,32,7,21,12,12);sack.close();}
    @Override protected void onDraw(Canvas canvas){super.onDraw(canvas);canvas.save();canvas.scale(getWidth()/40f,getHeight()/44f);paint.setColor(0xffececec);paint.setStyle(Paint.Style.FILL);canvas.drawRoundRect(0,0,40,44,10,10,paint);paint.setColor(0xffd4d4d4);canvas.drawPath(sack,paint);paint.setColor(0xff777777);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(1);canvas.drawLine(11,12,29,12,paint);canvas.drawLine(20,20,20,32,paint);canvas.drawLine(15,22,20,26,paint);canvas.drawLine(25,25,20,29,paint);canvas.restore();}
}
