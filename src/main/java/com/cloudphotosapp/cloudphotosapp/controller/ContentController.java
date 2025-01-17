package com.cloudphotosapp.cloudphotosapp.controller;

import com.cloudphotosapp.cloudphotosapp.DTO.FoldersPhotosDTO;
import com.cloudphotosapp.cloudphotosapp.bootstrap.CloudPhotosBootStrap;
import com.cloudphotosapp.cloudphotosapp.domain.FilePhoto;
import com.cloudphotosapp.cloudphotosapp.domain.Folder;
import com.cloudphotosapp.cloudphotosapp.domain.User;
import com.cloudphotosapp.cloudphotosapp.repository.FilePhotoRepository;
import com.cloudphotosapp.cloudphotosapp.repository.FolderRepository;
import com.cloudphotosapp.cloudphotosapp.repository.RoleRepository;
import com.cloudphotosapp.cloudphotosapp.repository.UserRepository;
import com.cloudphotosapp.cloudphotosapp.service.CopyPasteService;
import com.cloudphotosapp.cloudphotosapp.service.FolderService;
import com.cloudphotosapp.cloudphotosapp.service.ImageService;
import com.cloudphotosapp.cloudphotosapp.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@Slf4j
@Controller
public class ContentController {
    private final UserService userService;
    private final RoleRepository roleRepository;
    private final FolderService folderService;
    private final ImageService imageService;
    private final FilePhotoRepository filePhotoRepository;
    private final UserRepository userRepository;
    private final FolderRepository folderRepository;
    private final CopyPasteService copyPasteService;

    //AMOUNT_OF_PHOTOS_IN_RAW should be modified together with <td class="col-md-3"> in photos.html Here, 3 = 12/AMOUNT_OF_PHOTOS_IN_RAW
    static public int AMOUNT_OF_PHOTOS_IN_RAW = 4;

    public ContentController(UserService userService, RoleRepository roleRepository, FolderService folderService, ImageService imageService, FilePhotoRepository filePhotoRepository, UserRepository userRepository, FolderRepository folderRepository, CopyPasteService copyPasteService) {
        this.userService = userService;
        this.roleRepository = roleRepository;
        this.folderService = folderService;
        this.imageService = imageService;
        this.filePhotoRepository = filePhotoRepository;
        this.userRepository = userRepository;
        this.folderRepository = folderRepository;
        this.copyPasteService = copyPasteService;
    }

    @GetMapping(value={"/index", "/"})
    String getIndex(Model model){
        log.debug("ContentController.getIndex():");
        log.debug("amount of users in userRepository: " + userRepository.count());
        log.debug("amount of photos in filePhotoRepository: " + filePhotoRepository.count());
        log.debug("amount of folders in folderRepository: " + folderRepository.count());

        return "index";
    }

    /*
    @RequestMapping({"/error"})
    public String index() {
        return "forward:/index.html";
    }*/

    ///////////////////////  photos and folders      //////////////////

    @GetMapping("/photos/{folderId}")
    String getCurrentFolder(@AuthenticationPrincipal User activeUser, @PathVariable String folderId, Model model){//}, @ModelAttribute("currentFolder") Folder currentFolder){
        //Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        //String currentPrincipalName = authentication.getName();

        log.debug("ContentController.getCurrentFolder(): folderId = " + folderId);

        String currentPrincipalName = activeUser.getUsername();

        Folder folder = folderService.getFolderById(Long.decode(folderId));

        if (folder == null) {
            log.error("ContentController.getCurrentFolder: folder NOT FOUND id = " + folderId);
            return "redirect:/photos/";//todo add error
        }

        User user = (User) userService.loadUserByUsername(currentPrincipalName);

        if (folder.getOwners().contains(user) == false) {
            log.error("ContentController.getCurrentFolder: user " + user.getUsername() +
                      " does NOT OWN folder " + folder.getName() + " with folder id = " + folder.getId() );
            return "redirect:/photos/";//todo add error
        }

        FilePhoto staticPictureEditPen = filePhotoRepository.findByName(CloudPhotosBootStrap.PREDEFINED_STATIC_PICTURE_EDIT_PEN);

        if (staticPictureEditPen == null)
            log.error("staticPictureEditPen was NOT LOADED from filePhotoRepository in ContentController");

        FilePhoto staticPictureArrowDown = filePhotoRepository.findByName(CloudPhotosBootStrap.PREDEFINED_STATIC_PICTURE_EDIT_ARROW_DOWN);

        if (staticPictureEditPen == null)
            log.error("staticPictureArrowDown was NOT LOADED from filePhotoRepository in ContentController");

        FoldersPhotosDTO foldersPhotosDTO = new FoldersPhotosDTO(new ArrayList<>(folder.getFilePhotos()),
                                                                 new ArrayList<>(folder.getSubFolders()),
                                                     getSharedWithMeFoldersToShow (currentPrincipalName),
                                                     staticPictureEditPen, staticPictureArrowDown);


        if (folder.getParent() != null && folder.getParent().getOwners().contains(user))
            model.addAttribute("parent", folder.getParent());

        if (folder.getParent() != null && !folder.getParent().getOwners().contains(user))
            model.addAttribute("parent", user.getRoot());

        //the other user shared his own root folder and getParent() is null - then I add my own root as parent in order the user to be able to get back to his own folders
        if (folder.getParent() == null && folder.getOwners().contains(user) && folder.getOwners().size()>=2)
            model.addAttribute("parent", user.getRoot());


        model.addAttribute("foldersPhotosDTO", foldersPhotosDTO);

        model.addAttribute("folders", folder.getSubFolders());
        model.addAttribute("photos", folder.getFilePhotos());
        model.addAttribute("currentFolder", folder);

        addPhotosTableToModel (model, folder.getFilePhotos());

        return "photos";
    }

    private void addPhotosTableToModel (Model model,List<FilePhoto> photos) {
      //  Set<FilePhoto> photos = folder.getFilePhotos();

        int n = AMOUNT_OF_PHOTOS_IN_RAW;
        int m = (int)Math.ceil(photos.size()/((double)n));

        Iterator<FilePhoto> iterator = photos.iterator();

        ArrayList<ArrayList<FilePhoto>> photosTable = new ArrayList<>();
        for (int j = 0; j < m; j ++) {

            ArrayList<FilePhoto> photosRaw = new ArrayList<>();

            for (int i = 0; i < n && iterator.hasNext(); i++)
                photosRaw.add(iterator.next());

            photosTable.add(photosRaw);
        }

        model.addAttribute("photosTable", photosTable);
        model.addAttribute("photosRawSize", AMOUNT_OF_PHOTOS_IN_RAW);
    }

    private List<Folder> getSharedWithMeFoldersToShow (String currentPrincipalName){
        List<Folder> sharedFolders = new ArrayList<>();

      //  Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
       // String currentPrincipalName = authentication.getName();

        User user = (User) userService.loadUserByUsername(currentPrincipalName);

        Set<Folder> allFolders = user.getFolders();

        for (Folder cur : allFolders){
            Folder parent = cur;

            if (parent.getOwners().contains(user) == false)
                continue;

            while (parent.getParent() != null && parent.getParent().getOwners().contains(user))
                parent = parent.getParent();

            if (user.getRoot() != null && user.getRoot().equals(parent) == false &&
                    sharedFolders.contains(parent) == false)
                sharedFolders.add(parent);
        }

        log.debug("ContentController.getSharedWithMeFoldersToShow(); resulting array:");

        for (Folder folder : sharedFolders){
            log.debug("name = " + folder.getName() + " ; id = " + folder.getId());
        }

        return sharedFolders;
    }

    @GetMapping("/photos")
    String getPhotosAndUsersRoot(@AuthenticationPrincipal User activeUser, Model model){

       // Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
     //   String currentPrincipalName = authentication.getName();

        String currentPrincipalName = activeUser.getUsername();

        Folder root = folderService.getRootFolderByUsername (currentPrincipalName);

        if (root == null)
            return "photos";//todo add error


        FilePhoto photoPen = filePhotoRepository.findByName(CloudPhotosBootStrap.PREDEFINED_STATIC_PICTURE_EDIT_PEN);
        if (photoPen == null)
            log.error("DID NOT LOAD photoPen in ContentController.get..Root");

        log.debug("photoPen name = " + photoPen.getName());

        FilePhoto staticPictureEditPen = filePhotoRepository.findByName(CloudPhotosBootStrap.PREDEFINED_STATIC_PICTURE_EDIT_PEN);

        if (staticPictureEditPen == null)
            log.error("staticPictureEditPen was NOT LOADED from filePhotoRepository in ContentController");

        FilePhoto staticPictureArrowDown = filePhotoRepository.findByName(CloudPhotosBootStrap.PREDEFINED_STATIC_PICTURE_EDIT_ARROW_DOWN);

        if (staticPictureEditPen == null)
            log.error("staticPictureArrowDown was NOT LOADED from filePhotoRepository in ContentController");

        FoldersPhotosDTO foldersPhotosDTO = new FoldersPhotosDTO(new ArrayList<>(root.getFilePhotos()),
                                                                 new ArrayList<>(root.getSubFolders()),
                                                                 getSharedWithMeFoldersToShow (currentPrincipalName),
                                                                 staticPictureEditPen, staticPictureArrowDown);

        model.addAttribute("foldersPhotosDTO", foldersPhotosDTO);
        model.addAttribute("folders", root.getSubFolders());
        model.addAttribute("photos", root.getFilePhotos());
        model.addAttribute("currentFolder", root);

        addPhotosTableToModel (model, root.getFilePhotos());

        return "photos";
    }



    ////////////// search button
    @PostMapping("photos/startSearching/currentFolder/{currentFolderId}")
    public String startSearching (@AuthenticationPrincipal User activeUser, @PathVariable("currentFolderId") String currentFolderId, Model model){

        log.debug("ContentController.startSearching(..)");
        getCurrentFolder(activeUser, currentFolderId, model);

        model.addAttribute("SearchingIsBeingDoneNow", true);

        return "photos";
    }

    @PostMapping("photos/{currentFolderId}/search/")
    public String search (@ModelAttribute("foldersPhotosDTO") FoldersPhotosDTO foldersPhotosDTO, @PathVariable("currentFolderId") String currentFolderId, Model model){
        log.debug("ContentController.search(): searchPhrase = " + foldersPhotosDTO.getSearchPhrase());


        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String currentPrincipalName = authentication.getName();

        Folder currentFolder = folderService.getFolderById(Long.decode(currentFolderId));

        if (currentFolder == null) {
            log.error("ContentController.search: currentFolder NOT FOUND id = " + currentFolderId);
            return "redirect:/photos/";//todo add error
        }

        User user = (User) userService.loadUserByUsername(currentPrincipalName);

        if (foldersPhotosDTO.getSearchPhrase() == null || foldersPhotosDTO.getSearchPhrase().isEmpty() || foldersPhotosDTO.getSearchPhrase().isBlank()) {

            return "redirect:/photos/"+currentFolderId;
        }

        ArrayList<Folder> foldersSearchResult = new ArrayList<>();


        ArrayList<FilePhoto> photosSearchResult = new ArrayList<>();

        folderService.search (foldersSearchResult, photosSearchResult, foldersPhotosDTO.getSearchPhrase(), user);

        //Populate modelAttributes for searchResult.html page
        if (currentFolder.getOwners().contains(user) == false) {
            log.error("ContentController.search: user " + user.getUsername() +
                    " does NOT OWN folder " + currentFolder.getName() + " with folder id = " + currentFolder.getId() );
            return "redirect:/photos/";//todo add error
        }

        FilePhoto staticPictureEditPen = filePhotoRepository.findByName(CloudPhotosBootStrap.PREDEFINED_STATIC_PICTURE_EDIT_PEN);

        if (staticPictureEditPen == null)
            log.error("staticPictureEditPen was NOT LOADED from filePhotoRepository in ContentController");

        FilePhoto staticPictureArrowDown = filePhotoRepository.findByName(CloudPhotosBootStrap.PREDEFINED_STATIC_PICTURE_EDIT_ARROW_DOWN);

        if (staticPictureEditPen == null)
            log.error("staticPictureArrowDown was NOT LOADED from filePhotoRepository in ContentController");

        FoldersPhotosDTO newFoldersPhotosDTO = new FoldersPhotosDTO(photosSearchResult,
                foldersSearchResult, getSharedWithMeFoldersToShow (currentPrincipalName),
                staticPictureEditPen, staticPictureArrowDown);

        model.addAttribute("foldersPhotosDTO", newFoldersPhotosDTO);

        model.addAttribute("folders", foldersSearchResult);
        model.addAttribute("photos", photosSearchResult);
        model.addAttribute("currentFolder", currentFolder);

        addPhotosTableToModel (model, new ArrayList<>(photosSearchResult));

        return "searchResults";
    }

    ////////////// create new folder
    @PostMapping("photos/startCreatingNewFolder/currentFolder/{currentFolderId}")
    public String startCreatingNewFolder (@AuthenticationPrincipal User activeUser, @PathVariable("currentFolderId") String currentFolderId, Model model){

        log.debug("ContentController.startCreatingNewFolder(..)");
        getCurrentFolder(activeUser, currentFolderId, model);

        model.addAttribute("newFolderIsBeingCreated", true);

        return "photos";
    }

    @PostMapping("photos/{currentFolderId}/createNewFolder/")
    public String addNewFolder (@ModelAttribute("foldersPhotosDTO") FoldersPhotosDTO foldersPhotosDTO, @PathVariable("currentFolderId") String currentFolderId, Model model){
        log.debug("ContentController.addNewFolder(): newFolderName = " + foldersPhotosDTO.getNewFolderName());
        if (foldersPhotosDTO.getNewFolderName() != null && foldersPhotosDTO.getNewFolderName().isEmpty()==false && foldersPhotosDTO.getNewFolderName().isBlank()==false)
            folderService.createNewFolder(Long.valueOf(currentFolderId), foldersPhotosDTO.getNewFolderName());

        return "redirect:/photos/"+currentFolderId;
    }

    //////////////              working with selected folders and photos

    //start sharing selected folders - setting the flag variable
    @PostMapping("photos/startSharingFolders/currentFolder/{currentFolderId}")
    public String startSharingFolders (@AuthenticationPrincipal User activeUser, @ModelAttribute("foldersPhotosDTO") FoldersPhotosDTO foldersPhotosDTO, @PathVariable("currentFolderId") String currentFolderId, Model model){
        log.debug("ContentController.startSharingFolders(..)");
        getCurrentFolder(activeUser, currentFolderId, model);

        model.addAttribute("foldersAreBeingShared", true);

        FilePhoto staticPictureEditPen = filePhotoRepository.findByName(CloudPhotosBootStrap.PREDEFINED_STATIC_PICTURE_EDIT_PEN);

        if (staticPictureEditPen == null)
            log.error("staticPictureEditPen was NOT LOADED from filePhotoRepository in ContentController");

        foldersPhotosDTO.setStaticPictureEditPen(staticPictureEditPen);

        model.addAttribute(foldersPhotosDTO);

        return "photos";
    }

    @PostMapping("photos/{currentFolderId}/shareFolders/")
    public String shareFolders (@ModelAttribute("foldersPhotosDTO") FoldersPhotosDTO foldersPhotosDTO, @PathVariable("currentFolderId") String currentFolderId, Model model){
        log.debug("ContentController.shareFolders(): newOwnerName = " + foldersPhotosDTO.getNewOwnerName());

        if (foldersPhotosDTO.getNewOwnerName() != null && foldersPhotosDTO.getNewOwnerName().isEmpty()==false && foldersPhotosDTO.getNewOwnerName().isBlank()==false){
            User newUser = null;
            try {
                newUser = (User) userService.loadUserByUsername(foldersPhotosDTO.getNewOwnerName());
            }catch (UsernameNotFoundException e){
                log.error("ContentController.shareFolders: newOwner USER NOT FOUND, NAME = " + foldersPhotosDTO.getNewOwnerName());
                //todo add error message here
                return "redirect:/photos/"+currentFolderId;
            }

            folderService.shareSelectedFolders(Long.valueOf(currentFolderId), foldersPhotosDTO, newUser);
        }


        return "redirect:/photos/"+currentFolderId;
    }

    @PostMapping("photos/{currentFolderId}/copySelected/")
    public String copySelectedPhotosAndFolders (@PathVariable("currentFolderId") String currentFolderId, @ModelAttribute("foldersPhotosDTO") FoldersPhotosDTO foldersPhotosDTO) {

        List<Long> copiedPhotosIds = new ArrayList<>();
        List<Long> copiedFoldersIds = new ArrayList<>();

        for (Folder folder : foldersPhotosDTO.getFolders())
            if (folder.getIsSelected())
                copiedFoldersIds.add(folder.getId());

        for (Folder folder : foldersPhotosDTO.getSharedFolders())
            if (folder.getIsSelected())
                copiedFoldersIds.add(folder.getId());

        for (FilePhoto filePhoto : foldersPhotosDTO.getPhotos())
            if (filePhoto.getIsSelected())
                copiedPhotosIds.add(filePhoto.getId());

        copyPasteService.setCopied(copiedPhotosIds,copiedFoldersIds);

        return "redirect:/photos/"+currentFolderId;
    }

    @PostMapping("photos/{currentFolderId}/pasteSelected/")
    public String pasteSelectedPhotosAndFolders (@PathVariable("currentFolderId") String currentFolderId, @ModelAttribute("foldersPhotosDTO") FoldersPhotosDTO foldersPhotosDTO) {

        copyPasteService.pasteCopied(Long.valueOf(currentFolderId));

        return "redirect:/photos/"+currentFolderId;
    }

    @PostMapping("photos/{folderId}/deleteSelected/")
    public String deleteSelectedPhotosAndFolders (@PathVariable String folderId, @ModelAttribute("foldersPhotosDTO") FoldersPhotosDTO foldersPhotosDTO) {
      /*  log.debug("ContentController.testPostBooleanListFirst: folders.size() = " + foldersPhotosDTO.getFolders().size()
        + " ; photos.size() = " + foldersPhotosDTO.getPhotos().size());

        log.debug("My selected folders:");
        for (Folder folder : foldersPhotosDTO.getFolders())
            log.debug("name  = " + folder.getName() + " ; id = " + folder.getId() + " ; isSelected = " + folder.getIsSelected());

        log.debug("Selected photos:");
        for (FilePhoto photo : foldersPhotosDTO.getPhotos())
            log.debug("name  = " + photo.getName() + " ; id = " + photo.getId() + " ; isSelected = " + photo.getIsSelected());
*/
        // delete selected folders by Ids:
        Set<Long> foldersIdToBeDeleted = new HashSet<>();

        for (Folder folder : foldersPhotosDTO.getFolders())
            if (folder.getIsSelected())
                foldersIdToBeDeleted.add(folder.getId());

        for (Folder folder : foldersPhotosDTO.getSharedFolders())
            if (folder.getIsSelected())
                foldersIdToBeDeleted.add(folder.getId());

        if (!foldersIdToBeDeleted.isEmpty())
            folderService.deleteFoldersById(foldersIdToBeDeleted);

        //delete selected photos by Ids:
        Set<Long> photosIdToBeDeleted = new HashSet<>();

        for (FilePhoto photo : foldersPhotosDTO.getPhotos())
            if (photo.getIsSelected())
                photosIdToBeDeleted.add(photo.getId());

        if (!photosIdToBeDeleted.isEmpty())
            imageService.deletePhotosById(photosIdToBeDeleted);

        return "redirect:/photos/"+folderId;
    }

    //////////////////////////////////////////// working with editing folders and photos in the table

   @PostMapping("photos/switchSharedFolderBeingEdited/{currentFolderId}/selectFolder/{selectedFolderId}")
   public String switchSharedFolderBeingEditedStatus (@AuthenticationPrincipal User activeUser, @ModelAttribute("foldersPhotosDTO") FoldersPhotosDTO foldersPhotosDTO, @PathVariable("currentFolderId") String currentFolderId, @PathVariable("selectedFolderId") String selectedFolderId, Model model){

       Folder folder = folderService.getFolderById(Long.valueOf(selectedFolderId));

       if (folder!=null) {
           log.debug("ContentController.switchSharedFolderBeingEditedStatus() folder old name = " + folder.getName());

           if (folder.getBeingEdited())
               for (int i = 0; i < foldersPhotosDTO.getSharedFolders().size(); i ++)
                   if (foldersPhotosDTO.getSharedFolders().get(i).getId().equals(folder.getId())) {
                       log.debug("Setting new name = " + foldersPhotosDTO.getSharedFolders().get(i).getName());
                       folder.setName(foldersPhotosDTO.getSharedFolders().get(i).getName());
                       break;
                   }

           log.debug("ContentController.switchFolderBeingEditedStatus() folder new name = " + folder.getName());

           folder.setBeingEdited(!folder.getBeingEdited());

           folderService.save (folder);

           getCurrentFolder(activeUser, currentFolderId, model);

           log.debug("ContentController.switchSharedFolderBeingEditedStatus() worked fine");
           return "photos";
       }
       else {
           //todo add error
           log.error("ContentController.switchSharedFolderBeingEditedStatus: folder is NOT FOUND id = " + currentFolderId);
           return "photos";
       }
   }

    @PostMapping("photos/switchFolderBeingEdited/{currentFolderId}/selectFolder/{selectedFolderId}")
    public String switchFolderBeingEditedStatus (@AuthenticationPrincipal User activeUser, @ModelAttribute("foldersPhotosDTO") FoldersPhotosDTO foldersPhotosDTO, @PathVariable("currentFolderId") String currentFolderId, @PathVariable("selectedFolderId") String selectedFolderId, Model model){

        Folder folder = folderService.getFolderById(Long.valueOf(selectedFolderId));

        if (folder!=null) {
            log.debug("ContentController.switchFolderBeingEditedStatus() folder old name = " + folder.getName());

            if (folder.getBeingEdited())
                for (int i = 0; i < foldersPhotosDTO.getFolders().size(); i ++)
                    if (foldersPhotosDTO.getFolders().get(i).getId().equals(folder.getId())) {
                        log.debug("Setting new name = " + foldersPhotosDTO.getFolders().get(i).getName());
                        folder.setName(foldersPhotosDTO.getFolders().get(i).getName());
                        break;
                    }

            log.debug("ContentController.switchFolderBeingEditedStatus() folder new name = " + folder.getName());

            folder.setBeingEdited(!folder.getBeingEdited());

            folderService.save (folder);

            getCurrentFolder(activeUser, currentFolderId, model);

            log.debug("ContentController.switchFolderBeingEditedStatus() worked fine");
            return "photos";
        }
        else {
            //todo add error
            log.error("ContentController.switchFolderBeingEditedStatus: folder is NOT FOUND id = " + currentFolderId);
            return "photos";
        }
    }

    @PostMapping("photos/switchPhotoBeingEdited/{currentFolderId}/selectphoto/{selectedPhotoId}")
    public String switchPhotoBeingEditedStatus (@AuthenticationPrincipal User activeUser, @ModelAttribute("foldersPhotosDTO") FoldersPhotosDTO foldersPhotosDTO, @PathVariable String currentFolderId, @PathVariable String selectedPhotoId, Model model){
        Optional<FilePhoto> filePhotoOptional = filePhotoRepository.findById(Long.valueOf(selectedPhotoId));

        if (filePhotoOptional.isPresent()) {
            FilePhoto filePhoto = filePhotoOptional.get();

            if (filePhoto.getBeingEdited())
                for (int i = 0; i < foldersPhotosDTO.getPhotos().size(); i ++)
                    if (foldersPhotosDTO.getPhotos().get(i).getId().equals(Long.valueOf(selectedPhotoId)))
                        filePhoto.setName(foldersPhotosDTO.getPhotos().get(i).getName());

            filePhoto.setBeingEdited(!filePhoto.getBeingEdited());

            filePhotoRepository.save(filePhoto);

            getCurrentFolder(activeUser, currentFolderId, model);

            log.debug("ContentController.switchPhotoBeingEditedStatus() worked fine");
            return "photos";
        }
        else {
            //todo add error
            log.error("ContentController.switchPhotoBeingEditedStatus: filePhoto is NOT FOUND id = " + selectedPhotoId);
            return "photos";
        }
    }

    @GetMapping("photos/currentFolder/{currentFolderId}/selectphoto/{selectedPhotoId}")
    public String selectPhoto (@AuthenticationPrincipal User activeUser, @PathVariable String currentFolderId, @PathVariable String selectedPhotoId, Model model) {
        Optional<FilePhoto> filePhotoOptional = filePhotoRepository.findById(Long.valueOf(selectedPhotoId));

        if (filePhotoOptional.isPresent()) {
            FilePhoto filePhoto = filePhotoOptional.get();
            model.addAttribute("selectedFilePhoto", filePhoto);

            getCurrentFolder(activeUser, currentFolderId, model);

            log.debug("ContentController.selectPhoto(), photoName = " + filePhoto.getName());
            return "photos";
        }
        else {
            //todo add error
            log.error("ContentController.selectPhoto: filePhoto is NOT FOUND id = " + selectedPhotoId);
            return "photos";
        }
    }


}

/*
//Была попытка сделать href с img в форме карандашика edit. Все работало, кроме того что передавалось старое имя name
<a th:href="@{'/photos/switchPhotoBeingEdited/' + ${currentFolder.getId()} + '/selectphoto/' + ${photo.getId()} + '/photoName/' + ${foldersPhotosDTO.getPhotos().get(indRawStat.index*photosRawSize + indColStat.index).name}}">
    <img th:src="@{'/photos/' + ${foldersPhotosDTO.getStaticPictureEditPen().getId()} + '/image'}"
        th:class="img"
        th:alt="edit"
        width="8%" height="auto" th:if="${foldersPhotosDTO.getStaticPictureEditPen() != null}"
        th:alt-title="edit"
        th:title="edit">
</a>


//Пытаюсь сделать scroll таблицы с  - виден скролл, но не работает:
<table style="margin-top: 20px;" th:scrolling="yes">
<tbody style="margin-top: 20px;overflow-y: scroll; display: block">
 */
