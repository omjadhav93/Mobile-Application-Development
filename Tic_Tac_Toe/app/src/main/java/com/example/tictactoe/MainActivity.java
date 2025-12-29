package com.example.tictactoe;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class MainActivity extends AppCompatActivity {

    int player = 0, moves = 0;
    int[] gameState = {2, 2, 2, 2, 2, 2, 2, 2, 2};
    int[][] winningPositions = {{0, 1, 2}, {3, 4, 5}, {6, 7, 8}, {0, 3, 6}, {1, 4, 7}, {2, 5, 8}, {0, 4, 8}, {2, 4, 6}};

    public boolean isWinning() {
        for (int[] winningPosition : winningPositions) {
            boolean flag = true;
            for (int index : winningPosition) {
                if (gameState[index] != player) {
                    flag = false;
                    break;
                }
            }
            if (flag) {
                return true;
            }
        }
        return false;
    }

    public void winDisplay(){
        LinearLayout win = findViewById(R.id.win);
        LinearLayout play = findViewById(R.id.linearLayout);
        win.animate()
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(500) // Set the duration in milliseconds
                .withEndAction(() -> {
                    Button button = findViewById(R.id.button);
                    button.setEnabled(true);
                    TextView winner = findViewById(R.id.winner);
                    if(player == 0){
                        winner.setText(R.string.x_win);
                    }else{
                        winner.setText(R.string.o_win);
                    }
                    play.setEnabled(false);
                })
                .start(); // Start the animation
    }

    public void handleView(View view){
        TextView textView = (TextView) view;
        int index = Integer.parseInt(textView.getTag().toString()) - 1;
        LinearLayout win = findViewById(R.id.win);
        LinearLayout play = findViewById(R.id.linearLayout);
        TextView status = findViewById(R.id.status);
        if(gameState[index] == 2){
            if(player == 0) {
                gameState[index] = 0;
                textView.setText(R.string.x_mark);
                if (isWinning()) {
                    winDisplay();
                    status.animate().scaleX(0).scaleY(0).start();
                    return;
                }
                status.setText(R.string.o_turn);
                player = 1;
            }else{
                gameState[index] = 1;
                textView.setText(R.string.o_mark);
                if (isWinning()) {
                    winDisplay();
                    status.animate().scaleX(0).scaleY(0).start();
                    return;
                }
                status.setText(R.string.x_turn);
                player = 0;
            }
            moves++;
            if(moves == 9){
                win.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(500) // Set the duration in milliseconds
                        .withEndAction(() -> {
                            Button button = findViewById(R.id.button);
                            button.setEnabled(true);
                            TextView winner = findViewById(R.id.winner);
                            winner.setText(R.string.draw);
                            play.setEnabled(false);
                        })
                        .start(); // Start the animation
            }
        }
    }

    public void reset(View view){
        LinearLayout win = findViewById(R.id.win);
        LinearLayout play = findViewById(R.id.linearLayout);
        TextView status = findViewById(R.id.status);
        win.animate()
                .scaleX(0f)
                .scaleY(0f)
                .setDuration(500)
                .withStartAction(() -> {
                    for(int i = 0; i < play.getChildCount(); i++) {
                        LinearLayout row = (LinearLayout) play.getChildAt(i);
                        for (int j = 0; j < row.getChildCount(); j++) {
                            TextView textView = (TextView) row.getChildAt(j);
                            int index = Integer.parseInt(textView.getTag().toString()) - 1;
                            textView.setText(R.string.null_mark);
                            gameState[index] = 2;
                            status.animate().scaleX(1).scaleY(1).start();
                            status.setText(R.string.x_turn);
                            player = 0;
                            moves = 0;
                            Button button = findViewById(R.id.button);
                            button.setEnabled(false);
                        }
                    }
                    play.setEnabled(true);
                })
                .start(); // Start the animation
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
    }
}