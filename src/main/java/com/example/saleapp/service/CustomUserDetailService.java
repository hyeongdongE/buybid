package com.example.saleapp.service;

import com.example.saleapp.config.CustomUserDetail;
import com.example.saleapp.entity.UsersEntity;
import com.example.saleapp.repository.UsersRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CustomUserDetailService implements UserDetailsService {

    private final UsersRepository usersRepository;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        UsersEntity user = usersRepository.findByEmail(username).orElseThrow(()
                -> new IllegalArgumentException("이메일이 존재하지 않습니다."));

        return new CustomUserDetail(user);
    }
}