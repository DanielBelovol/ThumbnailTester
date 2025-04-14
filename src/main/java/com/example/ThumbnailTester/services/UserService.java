package com.example.ThumbnailTester.services;

import com.example.ThumbnailTester.data.user.UserData;
import com.example.ThumbnailTester.repositories.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class UserService {
    @Autowired
    private UserRepository userRepository;

    public boolean isExistById(long id) {
        return userRepository.isExistById(id);
    }

    public Optional<UserData> getById(long id) {
        return userRepository.findById(id);
    }
}
