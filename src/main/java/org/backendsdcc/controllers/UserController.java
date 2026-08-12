package org.backendsdcc.controllers;

import org.backendsdcc.services.UserService;
import org.backendsdcc.support.exceptions.AlreadyExistsException;
import org.backendsdcc.support.exceptions.NotFoundException;
import org.backendsdcc.support.messages.ResponseMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(value = "/user")
@CrossOrigin(
        origins = "http://localhost:4200",
        allowedHeaders = "*",
        methods = { RequestMethod.GET, RequestMethod.POST }
)
public class UserController
{
    @Autowired
    private UserService userService;

    @PostMapping("/register")
    @PreAuthorize("hasAnyRole('ROLE_user','ROLE_admin')")
    public ResponseEntity registerUser()
    {
        try
        {
            userService.createUser();
            return new ResponseEntity<>(HttpStatus.OK);
        } catch (AlreadyExistsException e)
        {
            return new ResponseEntity<>(new ResponseMessage("User already exists"), HttpStatus.OK);
        }
    }

    @GetMapping("/page")
    @PreAuthorize("hasAnyRole('ROLE_user','ROLE_admin')")
    public ResponseEntity userInfo()
    {
        try
        {
            return new ResponseEntity<>(userService.getCurrentUser(), HttpStatus.OK);
        } catch (NotFoundException e)
        {
            return new ResponseEntity<>(new ResponseMessage("User don't exists"), HttpStatus.NOT_FOUND);
        }
    }

}
