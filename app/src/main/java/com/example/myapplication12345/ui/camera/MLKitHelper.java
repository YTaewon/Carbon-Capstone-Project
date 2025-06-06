package com.example.myapplication12345.ui.camera;

import android.content.Context;
import android.graphics.Bitmap;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.label.ImageLabeler;
import com.google.mlkit.vision.label.ImageLabeling;
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class MLKitHelper {
    public interface LabelResultListener {
        void onResult(String combinedResult); // 라벨+텍스트 묶어서 전달
    }

    public static void analyzeImage(Context context, Bitmap bitmap, LabelResultListener listener) {
        InputImage image = InputImage.fromBitmap(bitmap, 0);

        // 이미지 라벨러와 텍스트 인식기
        ImageLabeler labeler = ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS);
        com.google.mlkit.vision.text.TextRecognizer textRecognizer =
                TextRecognition.getClient(new KoreanTextRecognizerOptions.Builder().build());

        // 라벨 맵핑 테이블 (오분류 시 교정)
        HashMap<String, String> labelMap = new HashMap<>();
        labelMap.put("tire", "headset");
        labelMap.put("steel", "pen");

        // 제외할 라벨
        List<String> banned = new ArrayList<>();
        banned.add("hand");
        banned.add("person");
        banned.add("finger");

        // 이미지 라벨링
        labeler.process(image)
                .addOnSuccessListener(labels -> {
                    List<String> filtered = new ArrayList<>();
                    int limit = Math.min(3, labels.size());
                    for (int i = 0; i < limit; i++) {
                        String labelText = labels.get(i).getText().toLowerCase();
                        boolean isBanned = false;
                        for (String ban : banned) {
                            if (labelText.contains(ban)) {
                                isBanned = true;
                                break;
                            }
                        }
                        if (!isBanned) {
                            String mapped = labelMap.getOrDefault(labelText, labels.get(i).getText());
                            filtered.add(mapped);
                        }
                    }

                    // OCR(텍스트 인식)
                    textRecognizer.process(image)
                            .addOnSuccessListener(textResult -> {
                                String detectedText = textResult.getText();
                                // 라벨과 텍스트를 합쳐서 반환
                                StringBuilder sb = new StringBuilder();
                                if (!filtered.isEmpty()) {
                                    sb.append("라벨: ");
                                    for (int i = 0; i < filtered.size(); i++) {
                                        sb.append(filtered.get(i));
                                        if (i < filtered.size() - 1) sb.append(", ");
                                    }
                                }
                                if (!detectedText.trim().isEmpty()) {
                                    if (sb.length() > 0) sb.append(" / ");
                                    sb.append("텍스트: ").append(detectedText.replace("\n", " "));
                                }
                                if (sb.length() == 0) {
                                    sb.append("인식실패");
                                }
                                listener.onResult(sb.toString());
                            })
                            .addOnFailureListener(e -> {
                                // OCR 실패 시 라벨만 반환
                                StringBuilder sb = new StringBuilder();
                                if (!filtered.isEmpty()) {
                                    sb.append("라벨: ");
                                    for (int i = 0; i < filtered.size(); i++) {
                                        sb.append(filtered.get(i));
                                        if (i < filtered.size() - 1) sb.append(", ");
                                    }
                                } else {
                                    sb.append("인식실패");
                                }
                                listener.onResult(sb.toString());
                            });
                })
                .addOnFailureListener(e -> listener.onResult("오류: " + e.getMessage()));
    }
}
