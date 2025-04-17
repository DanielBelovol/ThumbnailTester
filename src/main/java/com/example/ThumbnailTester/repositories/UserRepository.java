package com.example.ThumbnailTester.repositories;

import com.example.ThumbnailTester.data.user.UserData;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepository extends JpaRepository<UserData, Long> {
    boolean isExistById(Long id);
    UserData findByGoogleId(String googleId);

}
