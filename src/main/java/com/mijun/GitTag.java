package com.mijun;

import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;

public class GitTag extends GitCommit {

    // Constructor for GitTag
    public GitTag(byte[] data) {
        super(data);
    }
    
    // Default constructor
    public GitTag() {
        super();
    }

    @Override
    public byte[] type() {
        return "tag".getBytes();
    }
}
