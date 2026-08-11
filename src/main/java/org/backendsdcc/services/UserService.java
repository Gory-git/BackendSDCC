package org.backendsdcc.services;

import org.backendsdcc.models.PaymentMethod;
import org.backendsdcc.models.User;
import org.backendsdcc.repositories.UserRepository;
import org.backendsdcc.support.dto.PaymentMethodDTO;
import org.backendsdcc.support.dto.UserDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class UserService
{
    @Autowired
    private UserRepository userRepository;

    @Transactional(readOnly = true)
    public User findUser()
    {
        return null; // TODO FIND USER BY TOKEN JWT
    }

    @Transactional(readOnly = true)
    public User findUser(int id)
    {
        return null; // TODO FIND USER BY ID
    }

    @Transactional(readOnly = true)
    public void addUser(UserDTO userDTO)
    {
        // TODO IMPLEMENT USER ADDITION LOGIC
    }

}
