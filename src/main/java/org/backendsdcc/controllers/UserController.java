package org.backendsdcc.controllers;

import org.backendsdcc.services.ProductService;
import org.backendsdcc.services.UserService;
import org.backendsdcc.support.exceptions.ConflictException;
import org.backendsdcc.support.exceptions.NotFoundException;
import org.backendsdcc.support.messages.ResponseMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

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
    @Autowired
    private ProductService productService;

    @PostMapping("/register")
    @PreAuthorize("hasAnyRole('ROLE_USER','ROLE_ADMIN')")
    public ResponseEntity registerUser()
    {
        try
        {
            userService.createUser();
            return new ResponseEntity<>(HttpStatus.OK);
        } catch (ConflictException e)
        {
            return new ResponseEntity<>(new ResponseMessage("User already exists"), HttpStatus.CONFLICT);
        }
    }

    @GetMapping("/page")
    @PreAuthorize("hasAnyRole('ROLE_USER','ROLE_ADMIN')")
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

    @GetMapping("/product-of-the-month")
    @PreAuthorize("hasAnyRole('ROLE_USER','ROLE_ADMIN')")
    public ResponseEntity getProductOfTheMonth()
    {
        try
        {
            return new ResponseEntity<>(productService.getMostBoughtProductOfTheMonth(userService.getCurrentUser().getEmail(), Instant.now()), HttpStatus.OK);
        } catch (NotFoundException e)
        {
            return new ResponseEntity<>(new ResponseMessage("Product of the month not found"), HttpStatus.NOT_FOUND);
        }
    }

    @GetMapping("/product-of-time-span/{dateMin}/{dateMax}")
    @PreAuthorize("hasAnyRole('ROLE_USER','ROLE_ADMIN')")
    public ResponseEntity getProductOfTimeSpan(@PathVariable Instant dateMin, @PathVariable Instant dateMax)
    {
        try
        {
            return new ResponseEntity<>(productService.getMostBoughtProductOfTimeSpan(userService.getCurrentUser().getEmail(), dateMin, dateMax), HttpStatus.OK);
        } catch (NotFoundException e)
        {
            return new ResponseEntity<>(new ResponseMessage("Product of the month not found"), HttpStatus.NOT_FOUND);
        }
    }

}