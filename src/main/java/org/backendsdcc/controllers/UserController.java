package org.backendsdcc.controllers;

import jakarta.validation.Valid;
import org.backendsdcc.services.ProductService;
import org.backendsdcc.services.UserService;
import org.backendsdcc.support.dto.UserDTO;
import org.backendsdcc.support.dto.UserUpdateDTO;
import org.backendsdcc.support.exceptions.ConflictException;
import org.backendsdcc.support.exceptions.InvalidRequestException;
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
public class UserController
{
    @Autowired
    private UserService userService;
    @Autowired
    private ProductService productService;

    @PostMapping("/register")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public ResponseEntity registerUser(@RequestBody @Valid UserDTO userDTO)
    {
        try
        {
            userService.createUser(userDTO);
            return new ResponseEntity<>(new ResponseMessage("User created successfully."), HttpStatus.CREATED);
        } catch (ConflictException e)
        {
            return new ResponseEntity<>(new ResponseMessage("User already exists"), HttpStatus.CONFLICT);
        } catch (InvalidRequestException e)
        {
            return new ResponseEntity<>(new ResponseMessage(e.getMessage()), HttpStatus.BAD_REQUEST);
        }
    }

    @PutMapping("/update")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public ResponseEntity updateUser(@RequestBody @Valid UserUpdateDTO userUpdateDTO)
    {
        return new ResponseEntity<>(userService.updateCurrentUser(userUpdateDTO), HttpStatus.OK);
    }

    /**
     * Modifica il profilo di un altro utente. L'email viaggia come parametro e non come
     * variabile di percorso perché contiene un punto, che nel percorso richiederebbe di
     * disattivare il troncamento dell'estensione; {@code /find} usa già lo stesso stile.
     */
    @PutMapping("/update-by-email")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity updateUserByEmail(@RequestParam String email, @RequestBody @Valid UserUpdateDTO userUpdateDTO)
    {
        return new ResponseEntity<>(userService.updateUserByEmail(email, userUpdateDTO), HttpStatus.OK);
    }

    @GetMapping("/page")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
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
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
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

    @GetMapping("/product-of-time-span")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public ResponseEntity getProductOfTimeSpan(@RequestParam Instant dateMin, @RequestParam Instant dateMax)

    {
        try
        {
            return new ResponseEntity<>(productService.getMostBoughtProductOfTimeSpan(userService.getCurrentUser().getEmail(), dateMin, dateMax), HttpStatus.OK);
        } catch (NotFoundException e)
        {
            return new ResponseEntity<>(new ResponseMessage("Product of the month not found"), HttpStatus.NOT_FOUND);
        }
    }

    @GetMapping("/list")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity listUsers()
    {
        return new ResponseEntity<>(userService.getAllUsers(), HttpStatus.OK);
    }

    @GetMapping("/find")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity findUsers(@RequestParam String query, @RequestParam float threshold)
    {
        return new ResponseEntity<>(userService.searchUsers(query, threshold), HttpStatus.OK);
    }

}