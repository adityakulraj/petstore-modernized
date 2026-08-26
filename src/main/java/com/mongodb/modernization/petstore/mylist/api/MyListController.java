package com.mongodb.modernization.petstore.mylist.api;

import com.mongodb.modernization.petstore.mylist.application.MyListService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/my-list")
public class MyListController {
    private final MyListService myList;

    public MyListController(MyListService myList) {
        this.myList = myList;
    }

    @GetMapping
    MyListService.MyListView myList(Authentication authentication) {
        return myList.myList(authentication.getName());
    }

    @PostMapping("/items/{itemId}")
    MyListService.MyListView add(Authentication authentication, @PathVariable String itemId) {
        return myList.add(authentication.getName(), itemId);
    }

    @DeleteMapping("/items/{itemId}")
    MyListService.MyListView remove(Authentication authentication, @PathVariable String itemId) {
        return myList.remove(authentication.getName(), itemId);
    }
}
