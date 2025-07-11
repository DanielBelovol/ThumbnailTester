package com.example.ThumbnailTester.mapper;
import com.example.ThumbnailTester.Request.ThumbnailRequest; import com.example.ThumbnailTester.Request.ThumbnailTestConfRequest; import com.example.ThumbnailTester.data.thumbnail.*; import com.example.ThumbnailTester.data.user.UserData; import com.example.ThumbnailTester.dto.ImageOption; import com.example.ThumbnailTester.dto.ThumbnailQueueItem; import com.example.ThumbnailTester.services.UserService; import org.slf4j.Logger; import org.slf4j.LoggerFactory; import org.springframework.beans.factory.annotation.Autowired; import org.springframework.stereotype.Service;
import java.util.ArrayList; import java.util.List;
@Service public class Mapper { @Autowired private UserService userService; private static final Logger log = LoggerFactory.getLogger(Mapper.class);
    public ThumbnailTestConf testConfRequestToDTO(ThumbnailTestConfRequest request) {
        log.info("Entering testConfRequestToDTO method.");
        log.info("TestType: {}", request.getTestingType());
        log.info("TestingType: {}", request.getTestingMode());
        log.info("CriterionOfWinner: {}", request.getCriterionOfWinner());

        ThumbnailTestConf testConf = new ThumbnailTestConf();

        try {
            testConf.setTestType(TestingType.valueOf(request.getTestingType()));
        } catch (IllegalArgumentException e) {
            log.error("Invalid TestConfType: {}", request.getTestingType(), e);
            // Optionally set default or return null
        }

        try {
            testConf.setTestingMode(TestingMode.valueOf(request.getTestingMode()));
        } catch (IllegalArgumentException e) {
            log.error("Invalid TestingType: {}", request.getTestingType(), e);
        }

        testConf.setTestingByTimeMinutes(request.getTestingByTimeMinutes());
        testConf.setTestingByMetrics(request.getTestingByMetrics());

        try {
            testConf.setCriterionOfWinner(CriterionOfWinner.valueOf(request.getCriterionOfWinner()));
        } catch (IllegalArgumentException e) {
            log.error("Invalid CriterionOfWinner: {}", request.getCriterionOfWinner(), e);
            throw e;
        }
        log.info("Exiting testConfRequestToDTO method.");
        return testConf;
    }

    public ThumbnailData thumbnailRequestToData(ThumbnailRequest thumbnailRequest) {
        log.info("Entering thumbnailRequestToData method.");

        UserData userData = userService.getByGoogleId(thumbnailRequest.getUserDTO().getGoogleId());
        if (userData == null) {
            userData = new UserData(
                    thumbnailRequest.getUserDTO().getGoogleId(),
                    thumbnailRequest.getUserDTO().getRefreshToken()
            );
        }

        ThumbnailData thumbnailData = new ThumbnailData();
        thumbnailData.setVideoUrl(thumbnailRequest.getVideoUrl());
        thumbnailData.setTestConf(testConfRequestToDTO(thumbnailRequest.getTestConfRequest()));

        if (userData.getId() != null && userService.isExistById(userData.getId())) {
            thumbnailData.setUser(userData);
        } else {
            thumbnailData.setUser(userData); // or create a new UserData if needed
        }

        TestingType type = thumbnailData.getTestConf() != null ? thumbnailData.getTestConf().getTestType() : TestingType.THUMBNAIL;

        List<ImageOption> imageOptions = createImageOptions(
                thumbnailRequest.getImages(),
                thumbnailRequest.getTexts(),
                thumbnailData,
                type
        );
        thumbnailData.setImageOptions(imageOptions);
        return thumbnailData;
    }

    public ImageOption thumbnailDataToImageOption(ThumbnailQueueItem thumbnailQueueItem) {
        return thumbnailQueueItem.getImageOption();
    }

    public List<ImageOption> createImageOptions(List<String> fileUrls, List<String> texts, ThumbnailData thumbnailData, TestingType type) {
        List<ImageOption> imageOptions = new ArrayList<>();
        if (thumbnailData == null) {
            log.error("ThumbnailData is null when attempting to create image options");
            return imageOptions;
        }

        int maxIndex = Math.max(fileUrls != null ? fileUrls.size() : 0, texts != null ? texts.size() : 0);

        for (int i = 0; i < maxIndex; i++) {
            ImageOption option = new ImageOption();
            option.setThumbnail(thumbnailData);
            option.setWinner(false);
            option.setThumbnailStats(null);

            switch (type) {
                case THUMBNAIL:
                    if (fileUrls != null && i < fileUrls.size()) {
                        option.setFileUrl(fileUrls.get(i));
                    }
                    break;
                case TEXT:
                    if (texts != null && i < texts.size()) {
                        option.setText(texts.get(i));
                    }
                    break;
                case THUMBNAILTEXT:
                    if (fileUrls != null && i < fileUrls.size()) {
                        option.setFileUrl(fileUrls.get(i));
                    }
                    if (texts != null && i < texts.size()) {
                        option.setText(texts.get(i));
                    }
                    break;
            }

            imageOptions.add(option);
        }

        return imageOptions;
    }
}