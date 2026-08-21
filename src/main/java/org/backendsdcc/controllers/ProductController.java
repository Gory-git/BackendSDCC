package org.backendsdcc.controllers;

import org.backendsdcc.services.ProductService;
import org.backendsdcc.support.dto.ProductDTO;
import org.backendsdcc.support.exceptions.ConflictException;
import org.backendsdcc.support.exceptions.InvalidRequestException;
import org.backendsdcc.support.exceptions.NotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping(value = "/product")
@CrossOrigin(
        origins = "http://localhost:5173",
        allowedHeaders = "*",
        methods = { RequestMethod.GET, RequestMethod.POST }
)
public class ProductController
{
    @Autowired
    private ProductService productService;

    @GetMapping("/all")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public ResponseEntity<List<ProductDTO>> getAllProducts()
    {
        List<ProductDTO> products = productService.getAllProducts();
        return new ResponseEntity<>(products, HttpStatus.OK);
    }

    @PostMapping("/add")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<String> addProduct(@RequestBody ProductDTO productDTO)
    {
        try
        {
            productService.addProduct(productDTO);
            return new ResponseEntity<>("Product added successfully", HttpStatus.CREATED);
        } catch (ConflictException e)
        {
            return new ResponseEntity<>(e.getMessage(), HttpStatus.CONFLICT);
        } catch (InvalidRequestException e)
        {
            return new ResponseEntity<>(e.getMessage(), HttpStatus.BAD_REQUEST);
        }
    }

    @GetMapping("/{code}")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public ResponseEntity<ProductDTO> getProductByCode(@PathVariable String code)
    {
        try
        {
            ProductDTO product = productService.getProductByCode(code);
            return new ResponseEntity<>(product, HttpStatus.OK);
        } catch (NotFoundException e)
        {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
    }

    @GetMapping("/product-of-the-month/{userEmail}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<ProductDTO> getProductOfTheMonth(@PathVariable String userEmail)
    {
        try
        {
            ProductDTO product = productService.getMostBoughtProductOfTheMonth(userEmail, Instant.now());
            return new ResponseEntity<>(product, HttpStatus.OK);
        } catch (NotFoundException e)
        {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
    }

    @GetMapping("/product-of-time-span/{userEmail}/{dateMin}/{dateMax}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<ProductDTO> getProductOfTimeSpan(@PathVariable String userEmail, @PathVariable Instant dateMin, @PathVariable Instant dateMax)
    {
        try
        {
            ProductDTO product = productService.getMostBoughtProductOfTimeSpan(userEmail, dateMin, dateMax);
            return new ResponseEntity<>(product, HttpStatus.OK);
        } catch (NotFoundException e)
        {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
    }

}